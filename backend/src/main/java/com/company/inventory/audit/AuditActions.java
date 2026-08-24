package com.company.inventory.audit;

public final class AuditActions {

    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_UPDATE = "PRODUCT_UPDATE";
    public static final String PRODUCT_DEACTIVATE = "PRODUCT_DEACTIVATE";

    public static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String CATEGORY_DELETE = "CATEGORY_DELETE";

    public static final String UNIT_CREATE = "UNIT_CREATE";
    public static final String UNIT_UPDATE = "UNIT_UPDATE";
    public static final String UNIT_DELETE = "UNIT_DELETE";

    public static final String STOCK_IN = "STOCK_IN";
    public static final String STOCK_OUT = "STOCK_OUT";
    public static final String STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";

    public static final String SUPPLIER_CREATE = "SUPPLIER_CREATE";
    public static final String SUPPLIER_UPDATE = "SUPPLIER_UPDATE";
    public static final String SUPPLIER_DELETE = "SUPPLIER_DELETE";

    public static final String PURCHASE_CREATE = "PURCHASE_CREATE";
    public static final String PURCHASE_RECEIVE = "PURCHASE_RECEIVE";
    public static final String PURCHASE_CANCEL = "PURCHASE_CANCEL";

    public static final String ISSUE_CREATE = "ISSUE_CREATE";
    public static final String ISSUE_UPDATE = "ISSUE_UPDATE";
    public static final String ISSUE_APPROVE = "ISSUE_APPROVE";
    public static final String ISSUE_COMPLETE = "ISSUE_COMPLETE";
    public static final String ISSUE_CANCEL = "ISSUE_CANCEL";

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String USER_DISABLE = "USER_DISABLE";
    public static final String USER_ENABLE = "USER_ENABLE";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";
    public static final String ROLE_CHANGE = "ROLE_CHANGE";

    public static final String BACKUP_CREATE = "BACKUP_CREATE";
    public static final String BACKUP_RESTORE = "BACKUP_RESTORE";
    public static final String BACKUP_DELETE = "BACKUP_DELETE";
    public static final String BACKUP_EXPORT = "BACKUP_EXPORT";
    public static final String BACKUP_IMPORT = "BACKUP_IMPORT";

    public static final String SETTINGS_CHANGE = "SETTINGS_CHANGE";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";
    public static final String ADMIN_BOOTSTRAP = "ADMIN_BOOTSTRAP";

    private AuditActions() {
    }
}
