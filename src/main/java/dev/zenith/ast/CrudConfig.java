package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CrudConfig {
    private final boolean enabled;
    private final boolean paginate;
    private final List<String> searchFields;
    private final List<String> filterFields;
    private final List<String> sortFields;

    public CrudConfig(boolean enabled, boolean paginate, List<String> searchFields, List<String> filterFields, List<String> sortFields) {
        this.enabled = enabled;
        this.paginate = paginate;
        this.searchFields = searchFields != null ? searchFields : new ArrayList<>();
        this.filterFields = filterFields != null ? filterFields : new ArrayList<>();
        this.sortFields = sortFields != null ? sortFields : new ArrayList<>();
    }

    public static CrudConfig defaults() {
        return new CrudConfig(true, true, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPaginate() {
        return paginate;
    }

    public List<String> getSearchFields() {
        return Collections.unmodifiableList(searchFields);
    }

    public List<String> getFilterFields() {
        return Collections.unmodifiableList(filterFields);
    }

    public List<String> getSortFields() {
        return Collections.unmodifiableList(sortFields);
    }
}
