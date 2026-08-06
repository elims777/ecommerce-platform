interface Props {
    /** URL плеера с бэкенда. Null — площадка неизвестна, встраивать нельзя */
    embedUrl: string | null;
    /** Исходная ссылка — запасной вариант, когда эмбед невозможен */
    videoUrl: string | null;
}

/**
 * Видео к новости. Ролик проигрывается прямо на странице, пользователь никуда не уходит.
 * Ссылка с неизвестной площадки не встраивается — иначе в iframe можно было бы подсунуть
 * произвольный сайт.
 */
const VideoEmbed = ({ embedUrl, videoUrl }: Props) => {
    if (!embedUrl) {
        if (!videoUrl) return null;
        return (
            <a href={videoUrl} target="_blank" rel="noopener noreferrer"
               style={{ color: 'var(--brand-navy)', textDecoration: 'underline' }}>
                Смотреть видео
            </a>
        );
    }

    return (
        <div style={{
            position: 'relative',
            width: '100%',
            aspectRatio: '16 / 9',
            borderRadius: 'var(--r-4)',
            overflow: 'hidden',
            background: '#000',
        }}>
            <iframe
                src={embedUrl}
                title="Видео к новости"
                style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', border: 0 }}
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                allowFullScreen
                loading="lazy"
            />
        </div>
    );
};

export default VideoEmbed;
