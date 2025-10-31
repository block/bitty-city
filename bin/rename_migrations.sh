#!/bin/bash

# This script renames SQL migration files to follow the Flyway naming convention
# Changes:
# 1. Lowercase 'v' to uppercase 'V'
# 2. Change underscore between date and time to period (20250414_1414 -> 20250414.1414)

MIGRATIONS_DIR="outie-jooq-provider/src/main/resources/migrations"

# Check if the migrations directory exists
if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "Error: Migrations directory not found at $MIGRATIONS_DIR"
  exit 1
fi

# Loop through all SQL files in the migrations directory
for file in "$MIGRATIONS_DIR"/v*.sql; do
  if [ -f "$file" ]; then
    # Extract the filename (without path)
    filename=$(basename "$file")

    # Create the new filename with proper Flyway convention
    # 1. Replace lowercase 'v' with uppercase 'V'
    # 2. Replace underscore between date and time with period
    new_filename=$(echo "$filename" | sed 's/^v/V/' | sed 's/\([0-9]\{8\}\)_\([0-9]\{4\}\)/\1.\2/')

    # Full path for the new file
    new_file="$MIGRATIONS_DIR/$new_filename"

    # Rename the file
    mv "$file" "$new_file"

    echo "Renamed: $filename -> $new_filename"
  fi
done

echo "Migration files renamed successfully to follow Flyway convention"