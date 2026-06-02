package com.catcher.app.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0011\u001a\u00020\u0012H\u0016J\u0012\u0010\u0013\u001a\u00020\u00122\b\u0010\u0014\u001a\u0004\u0018\u00010\u0015H\u0016R\u001b\u0010\u0003\u001a\u00020\u00048BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/catcher/app/service/MessageNotificationListener;", "Landroid/service/notification/NotificationListenerService;", "()V", "database", "Lcom/catcher/app/data/AppDatabase;", "getDatabase", "()Lcom/catcher/app/data/AppDatabase;", "database$delegate", "Lkotlin/Lazy;", "deletionKeywords", "", "", "excludedTexts", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "targetPackages", "", "onDestroy", "", "onNotificationPosted", "sbn", "Landroid/service/notification/StatusBarNotification;", "app_debug"})
public final class MessageNotificationListener extends android.service.notification.NotificationListenerService {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy database$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Set<java.lang.String> targetPackages = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> excludedTexts = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> deletionKeywords = null;
    
    public MessageNotificationListener() {
        super();
    }
    
    private final com.catcher.app.data.AppDatabase getDatabase() {
        return null;
    }
    
    @java.lang.Override()
    public void onNotificationPosted(@org.jetbrains.annotations.Nullable()
    android.service.notification.StatusBarNotification sbn) {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
}