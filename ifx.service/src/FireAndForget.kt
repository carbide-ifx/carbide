package ifx.service

/**
 * Marks a suspending Unit service operation as one-way.
 *
 * The caller only waits for the request to be sent; it does not receive confirmation that the
 * service operation completed. Suspending Unit operations without this annotation use
 * request-response and wait for the service to complete.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class FireAndForget
