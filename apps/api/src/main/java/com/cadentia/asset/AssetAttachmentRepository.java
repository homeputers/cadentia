package com.cadentia.asset;

import com.cadentia.asset.AssetModels.ArchiveAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.AssetAttachmentAuditEventRecord;
import com.cadentia.asset.AssetModels.AssetAttachmentRecord;
import com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode;
import com.cadentia.asset.AssetModels.CreateAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.ReorderAssetAttachmentCommand;
import java.util.List;
import java.util.UUID;

public interface AssetAttachmentRepository {

    AssetAttachmentRecord createAttachment(CreateAssetAttachmentCommand command);

    List<AssetAttachmentRecord> listAttachments(AssetAttachmentTargetTypeCode targetTypeCode, UUID targetId);

    AssetAttachmentRecord reorderAttachment(ReorderAssetAttachmentCommand command);

    AssetAttachmentRecord archiveAttachment(ArchiveAssetAttachmentCommand command);

    List<AssetAttachmentAuditEventRecord> listAuditEvents(UUID attachmentId);
}
