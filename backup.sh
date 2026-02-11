#!/bin/bash
# Wiki App Backup Script
# Creates a timestamped backup of PostgreSQL database and MinIO files

BACKUP_DIR="./backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="wiki-backup-${TIMESTAMP}"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_NAME}"

mkdir -p "${BACKUP_PATH}"

echo "=== Wiki App Backup: ${BACKUP_NAME} ==="

# 1. Backup PostgreSQL
echo "Backing up PostgreSQL..."
docker exec wiki-postgres pg_dump -U wiki -d wikidb > "${BACKUP_PATH}/wikidb.sql" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "  Database backup: OK ($(wc -c < "${BACKUP_PATH}/wikidb.sql" | tr -d ' ') bytes)"
else
    echo "  Database backup: FAILED (is wiki-postgres running?)"
fi

# 2. Backup MinIO data
echo "Backing up MinIO files..."
if [ -d "./data/minio" ]; then
    cp -r ./data/minio "${BACKUP_PATH}/minio"
    FILE_COUNT=$(find "${BACKUP_PATH}/minio" -type f 2>/dev/null | wc -l | tr -d ' ')
    echo "  MinIO backup: OK (${FILE_COUNT} files)"
else
    echo "  MinIO backup: SKIPPED (no local data/minio directory)"
fi

# 3. Create compressed archive
echo "Compressing..."
cd "${BACKUP_DIR}" && tar -czf "${BACKUP_NAME}.tar.gz" "${BACKUP_NAME}" && rm -rf "${BACKUP_NAME}"
ARCHIVE_SIZE=$(ls -lh "${BACKUP_NAME}.tar.gz" | awk '{print $5}')
cd - > /dev/null

echo ""
echo "Backup complete: ${BACKUP_DIR}/${BACKUP_NAME}.tar.gz (${ARCHIVE_SIZE})"
