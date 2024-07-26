package acme.manager.membership.service

import ifx.service.ErrorCode

enum class MembershipError(override val description: String): ErrorCode {
    StaffNotFound("Staff not found")
    ;

    override val code: String get() = name
}
