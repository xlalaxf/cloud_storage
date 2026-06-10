package com.cloudstorage.service;

import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.model.CloudFile;
import com.cloudstorage.model.StorageObject;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    public FileResponse toResponse(CloudFile file) {
        Long parentId = file.getParent() == null ? null : file.getParent().getId();
        StorageObject object = file.getObject();
        return new FileResponse(
                file.getId(),
                file.getOwner().getId(),
                parentId,
                file.getFileKind(),
                file.getName(),
                object == null ? file.getContentType() : object.getContentType(),
                object == null ? file.getExtension() : object.getExtension(),
                object == null ? file.getSizeBytes() : object.getSizeBytes(),
                file.getDownloadCount(),
                file.getCreatedAt(),
                file.getUpdatedAt());
    }
}
