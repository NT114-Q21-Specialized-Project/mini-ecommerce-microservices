package migration

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestCollectMigrationFilesSortsSQLFilesOnly(t *testing.T) {
	t.Helper()

	dir := t.TempDir()
	files := []string{
		"003_add_is_active.sql",
		"001_create_users_table.sql",
		"README.md",
		"002_add_role_to_users.sql",
	}

	for _, name := range files {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("-- test"), 0o644); err != nil {
			t.Fatalf("write temp file %s: %v", name, err)
		}
	}

	got, err := collectMigrationFiles(dir)
	if err != nil {
		t.Fatalf("collect migration files: %v", err)
	}

	want := []string{
		filepath.Join(dir, "001_create_users_table.sql"),
		filepath.Join(dir, "002_add_role_to_users.sql"),
		filepath.Join(dir, "003_add_is_active.sql"),
	}

	if !reflect.DeepEqual(got, want) {
		t.Fatalf("unexpected migration order\nwant: %#v\ngot:  %#v", want, got)
	}
}
