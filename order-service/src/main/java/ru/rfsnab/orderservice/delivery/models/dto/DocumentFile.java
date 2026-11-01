package ru.rfsnab.orderservice.delivery.models.dto;

import java.util.Arrays;
import java.util.Objects;

/** Скачанный документ: имя файла и содержимое. */
public record DocumentFile(String fileName, byte[] content) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentFile other)) {
            return false;
        }
        return Objects.equals(fileName, other.fileName) && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(fileName) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "DocumentFile[fileName=" + fileName + ", content=" + (content == null ? 0 : content.length) + " bytes]";
    }
}
