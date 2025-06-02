package common.model;

import java.util.List;

public record TaskList(
        List<FileInfo> outdatedFiles
) {
}
