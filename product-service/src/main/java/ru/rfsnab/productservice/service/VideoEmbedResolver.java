package ru.rfsnab.productservice.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ссылка на видео → URL плеера для встраивания, чтобы ролик проигрывался на нашей странице,
 * а не уводил пользователя на сторонний сайт.
 * <p>
 * Работает только с известными площадками: встраивать произвольный домен нельзя — это дало бы
 * возможность вставить в iframe что угодно.
 */
@Service
public class VideoEmbedResolver {

    private static final Pattern YOUTUBE = Pattern.compile(
            "(?i)(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/)|youtu\\.be/)([\\w-]{11})"
    );

    /** vk.com/video-12345_67890 либо vk.com/video_ext.php?oid=-12345&id=67890 */
    private static final Pattern VK = Pattern.compile(
            "(?i)vk\\.com/(?:video_ext\\.php\\?oid=(-?\\d+)&(?:amp;)?id=(\\d+)|video(-?\\d+)_(\\d+))"
    );

    private static final Pattern RUTUBE = Pattern.compile(
            "(?i)rutube\\.ru/(?:video|play/embed)/([a-f0-9]{32})"
    );

    /**
     * @return URL для iframe или null, если ссылка пустая либо площадка неизвестна
     */
    public String resolveEmbedUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        Matcher youtube = YOUTUBE.matcher(videoUrl);
        if (youtube.find()) {
            // nocookie-домен не ставит трекинг-куки до старта воспроизведения
            return "https://www.youtube-nocookie.com/embed/" + youtube.group(1);
        }

        Matcher vk = VK.matcher(videoUrl);
        if (vk.find()) {
            String oid = vk.group(1) != null ? vk.group(1) : vk.group(3);
            String id = vk.group(2) != null ? vk.group(2) : vk.group(4);
            return "https://vk.com/video_ext.php?oid=" + oid + "&id=" + id;
        }

        Matcher rutube = RUTUBE.matcher(videoUrl);
        if (rutube.find()) {
            return "https://rutube.ru/play/embed/" + rutube.group(1);
        }

        return null;
    }
}
