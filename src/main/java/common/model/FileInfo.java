package common.model;

import java.nio.file.Path;

public record FileInfo(
        String filePath,
        Long modificationDate
) {
}