package ru.obabok.client.util.Lavobsidian;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import ru.obabok.client.Scan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncObsidianScanner {
    public static void startAsyncScan(Level world, AABB range) {
        // Список для хранения результатов, чтобы не терять их между тиками
        List<BlockPos> results = new ArrayList<>();

        // Счетчик текущего процента (используем AtomicInteger для безопасности в многопоточности)
        AtomicInteger lastReportedPercent = new AtomicInteger(-1);

        Thread scanThread = new Thread(() -> {
            int minY = (int) Math.floor(range.minY);
            int maxY = (int) Math.floor(range.maxY);
            int totalLayers = maxY - minY + 1;

            // Вызываем наш основной сканер, но передаем ему кастомный Consumer
            ObsidianScanner.scan(world, range, (pos) -> {
                results.add(pos.immutable());
            }, (currentY) -> {
                // Логика отчета о прогрессе
                int layersProcessed = maxY - currentY + 1;
                int percent = (int) ((layersProcessed * 100.0) / totalLayers);

                // Округляем до десятков и проверяем, не отправляли ли мы уже этот процент
                int roundedPercent = (percent / 10) * 10;

                if (roundedPercent > lastReportedPercent.get() && roundedPercent <= 100) {
                    if (lastReportedPercent.compareAndSet(lastReportedPercent.get(), roundedPercent)) {
                        // Отправляем сообщение в главный поток
                        Minecraft.getInstance().execute(() -> {
                            if(Minecraft.getInstance().player != null){
                                Minecraft.getInstance().player.sendSystemMessage(
                                        Component.literal("[Lavabsidian] Progress: " + roundedPercent + "%")
                                );
                            }
                        });
                    }
                }
            });

            // Финальное сообщение
            Minecraft.getInstance().execute(() -> {
                if(Minecraft.getInstance().player != null){
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§a[Lavabsidian] Сканирование завершено! Найдено точек: " + results.size())
                    );
                }
                Scan.selectedBlocks.addAll(results);
            });

        }, "ObsidianScanner-Thread");

        scanThread.setDaemon(true); // Поток умрет вместе с игрой
        scanThread.start();
    }
}
