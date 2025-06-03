package common.model;

import java.util.List;

public record ClientData(
        String clientId,
        List<FileInfo> files
){}
