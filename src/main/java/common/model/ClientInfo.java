package common.model;

import java.util.List;

public record ClientInfo (
        String clientId,
        List<FileInfo> files
){}
