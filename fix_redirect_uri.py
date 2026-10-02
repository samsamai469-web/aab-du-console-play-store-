import re

with open("app/src/main/java/com/example/ui/SamAiViewModel.kt", "r") as f:
    vm = f.read()

vm = vm.replace(
    'val redirectUri = "https://ais-dev-h5qu2a6gtl2p4qcuhid25d-151105670688.europe-west2.run.app/auth/callback"',
    'val redirectUri = "sam-ai://auth/callback"'
)

with open("app/src/main/java/com/example/ui/SamAiViewModel.kt", "w") as f:
    f.write(vm)
