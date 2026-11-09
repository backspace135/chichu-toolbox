package rikka.shizuku.server;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    public static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";
    public static final String MANAGER_APPLICATION_ID = "com.chichu.toolbox";
    public static final String PERMISSION_MANAGER = "com.chichu.toolbox.permission.MANAGER";
    public static final int BINDER_TRANSACTION_identity = 10002;
    public static final int BINDER_TRANSACTION_pendingPermission = 10003;
    public static final int BINDER_TRANSACTION_confirmPermission = 10004;
    public static final String EXTRA_PERMISSION_TOKEN = "com.chichu.toolbox.extra.PERMISSION_TOKEN";
    public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    public static final int BINDER_TRANSACTION_getApplications = 10001;
}
