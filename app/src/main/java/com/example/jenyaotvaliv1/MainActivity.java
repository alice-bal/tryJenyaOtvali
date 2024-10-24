package com.example.jenyaotvaliv1;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView statusTextView;                                    // текстовое поле для вывода
    private Set<String> knownDevices = new HashSet<>();                 // множество подключенных устройств
    private static final String CHANNEL_ID = "HotspotMonitorChannel";
    private static final String TARGET_IP = "192.168.43.41";            // IP ЖЕНЯ: целевой IP для блокировки

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);

        // Инициализация MediaPlayer для воспроизведения звука
        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound); // Замените на ваше аудио

        createNotificationChannel();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Process process = Runtime.getRuntime().exec("ip neigh show");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        StringBuilder result = new StringBuilder();
                        String line;
                        Set<String> currentDevices = new HashSet<>();
                        // boolean targetIpFound = false;
                        final boolean[] targetIpFound = {false};

                        while ((line = reader.readLine()) != null) {
                            result.append(line).append("\n");
                            currentDevices.add(line);

                            // Проверка, подключился ли нужный IP
                            if (line.contains(TARGET_IP)) {
                                targetIpFound[0] = true;
                                runOnUiThread(() -> {
                                    mediaPlayer.start(); // Воспроизведение аудиосигнала
                                    statusTextView.setText("Обнаружен и заблокирован: " + TARGET_IP);
                                });

                                // Попытка блокировки IP (требуется root-доступ)
                                try {
                                    Runtime.getRuntime().exec("iptables -A INPUT -s " + TARGET_IP + " -j DROP");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                // Отправка уведомления о подключении определенного IP
                                sendNotification("Блокировка IP", "Заблокирован IP: " + TARGET_IP);
                            }
                        }

                        // Проверяем, есть ли новые устройства
                        for (String device : currentDevices) {
                            if (!knownDevices.contains(device)) {
                                sendNotification("Новое устройство подключено", device);
                            }
                        }

                        knownDevices = currentDevices;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!targetIpFound[0]) {
                                    statusTextView.setText(result.toString());
                                }
                            }
                        });

                        Thread.sleep(10000); // Обновление каждые 10 секунд
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void sendNotification(String title, String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Hotspot Monitor Channel";
            String description = "Channel for Hotspot Monitor notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}

