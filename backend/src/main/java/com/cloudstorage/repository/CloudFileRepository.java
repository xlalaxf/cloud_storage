package com.cloudstorage.repository;

import com.cloudstorage.model.CloudFile;
import com.cloudstorage.model.FileKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CloudFileRepository extends JpaRepository<CloudFile, Long> {
    List<CloudFile> findByOwnerIdAndParentIsNullAndDeletedFalseOrderByFileKindDescNameAsc(Long ownerId);

    List<CloudFile> findByOwnerIdAndParentIdAndDeletedFalseOrderByFileKindDescNameAsc(Long ownerId, Long parentId);

    List<CloudFile> findByParentIdAndDeletedFalseOrderByFileKindDescNameAsc(Long parentId);

    List<CloudFile> findByOwnerIdAndDeletedFalseOrderByParentIdAscFileKindDescNameAsc(Long ownerId);

    Optional<CloudFile> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);

    Optional<CloudFile> findFirstByOwnerIdAndParentIdAndNameAndFileKindAndDeletedFalse(
            Long ownerId, Long parentId, String name, FileKind fileKind);

    boolean existsByOwnerIdAndParentIsNullAndNameAndDeletedFalse(Long ownerId, String name);

    boolean existsByOwnerIdAndParentIdAndNameAndDeletedFalse(Long ownerId, Long parentId, String name);

    boolean existsByOwnerIdAndParentIsNullAndNameAndDeletedFalseAndIdNot(Long ownerId, String name, Long id);

    boolean existsByOwnerIdAndParentIdAndNameAndDeletedFalseAndIdNot(Long ownerId, Long parentId, String name, Long id);

    @Modifying
    @Query("update CloudFile file set file.object = null where file.object.id = :objectId")
    int detachObject(Long objectId);
}
