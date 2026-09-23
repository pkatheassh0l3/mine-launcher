// TFC Create - lanzador del instalador visual.
// Incrusta la interfaz (app.ps1, WPF) y el logo, y los ejecuta con PowerShell de Windows.
package main

import (
	_ "embed"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

//go:embed app.ps1
var appScript []byte

//go:embed logo.png
var logoPng []byte

func msgBox(text string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	mb := user32.NewProc("MessageBoxW")
	t, _ := syscall.UTF16PtrFromString(text)
	c, _ := syscall.UTF16PtrFromString("TFC Create")
	mb.Call(0, uintptr(unsafe.Pointer(t)), uintptr(unsafe.Pointer(c)), 0x10)
}

func main() {
	exe, _ := os.Executable()
	dir := filepath.Join(os.Getenv("LOCALAPPDATA"), "tfc-create-pack")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		dir = os.TempDir()
	}
	script := filepath.Join(dir, "app.ps1")
	// BOM UTF-8 para que Windows PowerShell 5.1 lea bien las tildes
	if err := os.WriteFile(script, append([]byte{0xEF, 0xBB, 0xBF}, appScript...), 0o644); err != nil {
		msgBox("No se pudo preparar el instalador:\n" + err.Error())
		return
	}
	logo := filepath.Join(dir, "logo.png")
	_ = os.WriteFile(logo, logoPng, 0o644)

	ps := filepath.Join(os.Getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
	args := []string{"-NoProfile", "-ExecutionPolicy", "Bypass", "-STA", "-File", script}
	args = append(args, os.Args[1:]...)
	cmd := exec.Command(ps, args...)
	cmd.Env = append(os.Environ(),
		"TFC_SELF="+exe,
		"TFC_LOGO="+logo,
		"TFC_ARGS="+strings.Join(os.Args[1:], " "))
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	if err := cmd.Run(); err != nil {
		if _, ok := err.(*exec.ExitError); !ok {
			msgBox("No se pudo iniciar el instalador (PowerShell):\n" + err.Error())
		}
	}
}
