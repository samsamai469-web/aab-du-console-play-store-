import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import java.util.UUID

fun test() {
    GetSignInWithGoogleOption.Builder("client_id")
        .setNonce(UUID.randomUUID().toString())
        .build()
}
