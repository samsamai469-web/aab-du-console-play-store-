import re

with open("app/src/main/java/com/example/ui/SamAiViewModel.kt", "r") as f:
    vm = f.read()

bad_methods = """    private val _isAdminAuthenticated = MutableStateFlow(false)
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    val auditLogs: Flow<List<SecurityAuditLogEntity>> = auditDao?.getAllLogsDesc() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun authenticateAdminWith2FA(key: String, mfa: String): Pair<Boolean, String> {
        return if (key == "sam_ai_admin" && mfa == "123456") {
            _isAdminAuthenticated.value = true
            Pair(true, "Authentification réussie")
        } else {
            Pair(false, "Clé ou code MFA incorrect")
        }
    }

    fun logoutAdmin() {
        _isAdminAuthenticated.value = false
    }"""

good_methods = """    val auditLogs: Flow<List<SecurityAuditLogEntity>> = auditDao?.getRecentLogs(100) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun authenticateAdminWith2FA(key: String, mfa: String): Pair<Boolean, String> {
        return if (key == "sam_ai_admin" && mfa == "123456") {
            _adminAuthToken.value = "SAM_ADMIN_AUTH_VALIDATED_${System.currentTimeMillis()}"
            Pair(true, "Authentification réussie")
        } else {
            Pair(false, "Clé ou code MFA incorrect")
        }
    }

    fun logoutAdmin() {
        _adminAuthToken.value = null
    }"""

vm = vm.replace(bad_methods, good_methods)

with open("app/src/main/java/com/example/ui/SamAiViewModel.kt", "w") as f:
    f.write(vm)
