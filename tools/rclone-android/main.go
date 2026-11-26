// Trimmed rclone for Aura's private cloud (plan phase 5.1, registry row 886).
//
// It imports only what Aura uses: the OneDrive, Dropbox and local backends and the `rcd` and
// `serve http` commands (rclone's own fs/operations still pulls in the crypt backend). Everything
// else rclone can do (other clouds, mount, the sync commands, the self-updater) is left out: a
// smaller binary with less surface. Upstream's main (rclone.go)
// is these same lines with backend/all and cmd/all instead.
//
// Built by scripts/build-rclone-android.sh into librclone.so for each ABI; never committed.
package main

import (
	_ "github.com/rclone/rclone/backend/dropbox"
	_ "github.com/rclone/rclone/backend/local"
	_ "github.com/rclone/rclone/backend/onedrive"
	"github.com/rclone/rclone/cmd"
	_ "github.com/rclone/rclone/cmd/rcd"
	_ "github.com/rclone/rclone/cmd/serve/http"
)

func main() {
	cmd.Main()
}
