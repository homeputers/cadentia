package com.cadentia.reng.setlist;

import com.cadentia.generated.model.SetlistDiffOperation;
import com.cadentia.generated.model.SetlistItemChangeType;
import com.cadentia.generated.model.SetlistVersionDiffResponse;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionItemSnapshot;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SetlistVersionDiffService {

    public SetlistVersionDiffResponse diff(SetlistVersionSnapshot from, SetlistVersionSnapshot to) {
        Map<UUID, SetlistVersionItemSnapshot> fromItems = indexById(from.items());
        Map<UUID, SetlistVersionItemSnapshot> toItems = indexById(to.items());
        List<SetlistDiffOperation> operations = new ArrayList<>();

        for (SetlistVersionItemSnapshot source : from.items()) {
            SetlistVersionItemSnapshot target = toItems.get(source.id());
            if (target == null) {
                operations.add(new SetlistDiffOperation(SetlistItemChangeType.REMOVED)
                        .itemId(source.id())
                        .previousPosition(source.positionIndex() + 1)
                        .previousCatalogArrangementId(source.catalogArrangementId()));
                continue;
            }
            if (source.positionIndex() != target.positionIndex()) {
                operations.add(new SetlistDiffOperation(SetlistItemChangeType.REORDERED)
                        .itemId(source.id())
                        .previousPosition(source.positionIndex() + 1)
                        .newPosition(target.positionIndex() + 1));
            }
            if (!source.catalogArrangementId().equals(target.catalogArrangementId())) {
                operations.add(new SetlistDiffOperation(SetlistItemChangeType.REPLACED)
                        .itemId(source.id())
                        .previousCatalogArrangementId(source.catalogArrangementId())
                        .newCatalogArrangementId(target.catalogArrangementId()));
            }
            Integer sourceTranspose = transpose(source);
            Integer targetTranspose = transpose(target);
            if (!sourceTranspose.equals(targetTranspose)) {
                operations.add(new SetlistDiffOperation(SetlistItemChangeType.TRANSPOSED)
                        .itemId(source.id())
                        .previousTransposeSemitones(sourceTranspose)
                        .newTransposeSemitones(targetTranspose));
            }
        }

        for (SetlistVersionItemSnapshot target : to.items()) {
            if (!fromItems.containsKey(target.id())) {
                operations.add(new SetlistDiffOperation(SetlistItemChangeType.ADDED)
                        .itemId(target.id())
                        .newPosition(target.positionIndex() + 1)
                        .newCatalogArrangementId(target.catalogArrangementId())
                        .newTransposeSemitones(transpose(target)));
            }
        }

        operations.sort(Comparator.comparing(SetlistDiffOperation::getAction).thenComparing(op -> op.getItemId().toString()));
        return new SetlistVersionDiffResponse(from.setlistId(), from.versionId(), to.versionId(), operations);
    }

    private static Map<UUID, SetlistVersionItemSnapshot> indexById(List<SetlistVersionItemSnapshot> items) {
        Map<UUID, SetlistVersionItemSnapshot> map = new HashMap<>();
        for (SetlistVersionItemSnapshot item : items) {
            map.put(item.id(), item);
        }
        return map;
    }

    private static Integer transpose(SetlistVersionItemSnapshot item) {
        return item.transposedKey() == null ? 0 : 1;
    }
}
