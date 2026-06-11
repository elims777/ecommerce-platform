package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Component;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.config.IntegrationProperties.FtpProperties;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * Тонкий helper для FTP-доступа к серверу ФТК.
 * Каждый публичный метод открывает и закрывает соединение самостоятельно.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FtkFtpClient {

    private static final String GOODS_DIR = "/webdata/000000003/goods/1/";
    private static final String ROOT_DIR  = "/webdata/000000003/";

    private final IntegrationProperties properties;

    /**
     * Находит первый файл в указанной FTP-папке, имя которого начинается с prefix.
     * Возвращает полный FTP-путь к файлу или null если не найден.
     */
    public String findFileByPrefix(String ftpFolder, String prefix) throws IOException {
        FTPClient ftp = connect();
        try {
            FTPFile[] files = ftp.listFiles(ftpFolder);
            if (files == null) return null;
            for (FTPFile f : files) {
                if (f.isFile() && f.getName().startsWith(prefix)) {
                    return ftpFolder + f.getName();
                }
            }
            log.warn("Файл с префиксом '{}' не найден в {}", prefix, ftpFolder);
            return null;
        } finally {
            disconnect(ftp);
        }
    }

    /**
     * Скачивает файл по полному FTP-пути и возвращает InputStream.
     * Содержимое буферизируется в памяти — не использовать для файлов > 50 МБ.
     */
    public InputStream openStream(String ftpPath) throws IOException {
        byte[] data = downloadBytes(ftpPath);
        return new ByteArrayInputStream(data);
    }

    /**
     * Скачивает файл по полному FTP-пути в byte[].
     */
    public byte[] downloadBytes(String ftpPath) throws IOException {
        FTPClient ftp = connect();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean ok = ftp.retrieveFile(ftpPath, baos);
            if (!ok) {
                throw new IOException("FTP retrieveFile вернул false для: " + ftpPath);
            }
            log.debug("FTP скачан: {} ({} байт)", ftpPath, baos.size());
            return baos.toByteArray();
        } finally {
            disconnect(ftp);
        }
    }

    /**
     * Открывает InputStream для файла с FTP без буферизации в памяти.
     * Caller ОБЯЗАН закрыть возвращённый поток — это закроет FTP-соединение.
     * Использовать для больших файлов (prices ~125 МБ).
     */
    public FtpStreamHandle openLargeStream(String ftpPath) throws IOException {
        FtpProperties cfg = properties.getFtk().getFtp();
        int timeoutMs = properties.getFtk().getImageDownloadTimeoutSec() * 1000;

        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(timeoutMs);
        ftp.setDefaultTimeout(timeoutMs);
        ftp.setDataTimeout(Duration.ofMillis(timeoutMs));

        ftp.connect(cfg.getHost(), cfg.getPort());
        ftp.login(cfg.getUsername(), cfg.getPassword());
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);

        InputStream is = ftp.retrieveFileStream(ftpPath);
        if (is == null) {
            disconnect(ftp);
            throw new IOException("FTP: файл не найден: " + ftpPath);
        }
        log.debug("FTP стрим открыт: {}", ftpPath);
        return new FtpStreamHandle(is, ftp);
    }

    public String getGoodsDir() { return GOODS_DIR; }
    public String getRootDir()  { return ROOT_DIR; }

    private FTPClient connect() throws IOException {
        FtpProperties cfg = properties.getFtk().getFtp();
        int timeoutMs = properties.getFtk().getImageDownloadTimeoutSec() * 1000;

        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(timeoutMs);
        ftp.setDefaultTimeout(timeoutMs);
        ftp.setDataTimeout(Duration.ofMillis(timeoutMs));

        ftp.connect(cfg.getHost(), cfg.getPort());
        ftp.login(cfg.getUsername(), cfg.getPassword());
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }

    private void disconnect(FTPClient ftp) {
        if (ftp.isConnected()) {
            try { ftp.logout(); } catch (Exception ignored) {}
            try { ftp.disconnect(); } catch (Exception ignored) {}
        }
    }

    /**
     * Держит открытое FTP-соединение вместе с потоком данных.
     * Закрытие потока автоматически завершает FTP-сессию.
     */
    public static class FtpStreamHandle implements AutoCloseable {
        private final InputStream stream;
        private final FTPClient ftp;

        FtpStreamHandle(InputStream stream, FTPClient ftp) {
            this.stream = stream;
            this.ftp = ftp;
        }

        public InputStream getStream() { return stream; }

        @Override
        public void close() {
            try { stream.close(); } catch (Exception ignored) {}
            try { ftp.completePendingCommand(); } catch (Exception ignored) {}
            if (ftp.isConnected()) {
                try { ftp.logout(); } catch (Exception ignored) {}
                try { ftp.disconnect(); } catch (Exception ignored) {}
            }
        }
    }
}
