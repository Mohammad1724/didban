package org.didban.monitor

internal enum class SecurityMessage {
    HTTP_DISABLED,
    SERVER_SAVE_FAILED,
    SERVER_DELETE_FAILED,
    SERVER_READ_FAILED,
    SERVER_WRITE_BLOCKED,
    SERVER_DELETE_BLOCKED,
    BACKUP_PASSWORD_REQUIRED,
    PAGE_GUIDE
}

internal fun securityMessage(language: String, message: SecurityMessage): String {
    val fa = language == "fa"
    return when (message) {
        SecurityMessage.HTTP_DISABLED -> if (fa) "اتصال HTTP ناامن غیرفعال است؛ TLS را فعال کنید." else "Insecure HTTP connections are disabled; enable TLS."
        SecurityMessage.SERVER_SAVE_FAILED -> if (fa) "ذخیره امن سرور ناموفق بود." else "Secure server storage failed."
        SecurityMessage.SERVER_DELETE_FAILED -> if (fa) "حذف امن سرور ناموفق بود." else "Secure server deletion failed."
        SecurityMessage.SERVER_READ_FAILED -> if (fa) "خواندن امن فهرست سرورها ناموفق بود؛ داده موجود با فهرست خالی جایگزین نشده است." else "Secure server data could not be read; existing data has not been replaced with an empty list."
        SecurityMessage.SERVER_WRITE_BLOCKED -> if (fa) "تا رفع خطای حافظه امن، ذخیره‌سازی برای جلوگیری از بازنویسی داده متوقف است." else "Saving is blocked until secure-storage access is restored, preventing data overwrite."
        SecurityMessage.SERVER_DELETE_BLOCKED -> if (fa) "حذف تا رفع خطای حافظه امن متوقف است." else "Deletion is blocked until secure storage is available."
        SecurityMessage.BACKUP_PASSWORD_REQUIRED -> if (fa) "برای جلوگیری از افشای توکن‌ها، واردکردن رمز بکاپ الزامی است." else "A backup password is required to prevent credential exposure."
        SecurityMessage.PAGE_GUIDE -> if (fa) "راهنمای این صفحه" else "Page guide"
    }
}
