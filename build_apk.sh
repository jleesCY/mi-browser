#!/bin/bash

# ==========================================
# 1. Parse Command Line Arguments
# ==========================================
VERSION=""

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -v|--version) VERSION="$2"; shift ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

if [ -z "$VERSION" ]; then
    echo "Error: You must provide a version using -v or --version"
    exit 1
fi

echo "Starting build process for version: $VERSION"

# ==========================================
# 2. Run EAS Build & Capture Output
# ==========================================
echo "Running: eas build -p android --local"
BUILD_OUTPUT=$(eas build -p android --local | tee /dev/tty)

# ==========================================
# 3. Extract the .aab File Path
# ==========================================
AAB_PATH=$(echo "$BUILD_OUTPUT" | grep "You can find the build artifacts in" | awk '{print $NF}')

if [ -z "$AAB_PATH" ]; then
    echo "Error: Could not find the .aab file path in the build output."
    exit 1
fi

echo "Captured AAB path: $AAB_PATH"

# ==========================================
# 4. Run Bundletool (Generate APKS)
# ==========================================
OUTPUT_APKS="./builds/${VERSION}.apks"
BUNDLETOOL="./builds/bundletool-all-1.18.3.jar"

echo "Generating APKS..."
java -jar "$BUNDLETOOL" build-apks \
    --bundle="$AAB_PATH" \
    --output="$OUTPUT_APKS" \
    --mode=universal

# ==========================================
# 5. Rename .apks to .zip
# ==========================================
ZIP_FILE="./builds/${VERSION}.zip"
echo "Renaming $OUTPUT_APKS to $ZIP_FILE..."
mv "$OUTPUT_APKS" "$ZIP_FILE"

# ==========================================
# 6. Extract Zip & Isolate universal.apk
# ==========================================
TEMP_DIR="./builds/temp_${VERSION}"
FINAL_APK="./builds/${VERSION}.apk"

echo "Extracting to temporary directory $TEMP_DIR..."
unzip -q "$ZIP_FILE" -d "$TEMP_DIR"

if [ -f "$TEMP_DIR/universal.apk" ]; then
    echo "Found universal.apk. Moving to $FINAL_APK..."
    mv "$TEMP_DIR/universal.apk" "$FINAL_APK"
else
    echo "Error: 'universal.apk' was not found inside the generated zip!"
    # Clean up temp dir before exiting so we don't leave trash
    rm -rf "$TEMP_DIR"
    exit 1
fi

# ==========================================
# 7. Cleanup
# ==========================================
echo "Cleaning up intermediate files..."

# Delete the zip file
rm "$ZIP_FILE"

# Delete the temporary extraction folder and its contents
rm -rf "$TEMP_DIR"

# Delete the original .aab file from Step 2
rm "$AAB_PATH"

echo "------------------------------------------------"
echo "Success! The final file is ready:"
echo "$FINAL_APK"
echo "------------------------------------------------"