#!/bin/bash
# Wiki App Restore Script
# Restores from a backup archive created by backup.sh

if [ -z "$1" ]; then
    echo "Usage: ./restore.sh <backup-file.tar.gz>"
    echo ""
    echo "Available backups:"
    ls -lh ./backups/*.tar.gz 2>/dev/null || echo "  No backups found in ./backups/"
    exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "${BACKUP_FILE}" ]; then
    echo "Error: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

echo "=== Wiki App Restore ==="
echo "Restoring from: ${BACKUP_FILE}"
echo ""
read -p "This will OVERWRITE current data. Continue? (y/N) " CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo "Cancelled."
    exit 0
fi

# Extract backup
TEMP_DIR=$(mktemp -d)
tar -xzf "${BACKUP_FILE}" -C "${TEMP_DIR}"
BACKUP_DIR=$(ls "${TEMP_DIR}")
BACKUP_PATH="${TEMP_DIR}/${BACKUP_DIR}"

# 1. Restore PostgreSQL
if [ -f "${BACKUP_PATH}/wikidb.sql" ]; then
    echo "Restoring PostgreSQL..."
    docker exec -i wiki-postgres psql -U wiki -d wikidb -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" 2>/dev/null
    docker exec -i wiki-postgres psql -U wiki -d wikidb < "${BACKUP_PATH}/wikidb.sql" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "  Database restore: OK"
    else
        echo "  Database restore: FAILED"
    fi
else
    echo "  No database backup found in archive"
fi

# 2. Restore MinIO data
if [ -d "${BACKUP_PATH}/minio" ]; then
    echo "Restoring MinIO files..."
    mkdir -p ./data/minio
    cp -r "${BACKUP_PATH}/minio/"* ./data/minio/ 2>/dev/null
    echo "  MinIO restore: OK"
else
    echo "  No MinIO backup found in archive"
fi

# Cleanup
rm -rf "${TEMP_DIR}"

echo ""
echo "Restore complete. Restart services: docker-compose restart"
