#!/bin/sh

## Usage: $(basename "$0") [-h] [arg1]
##
## Builds an installable APK file from the source code
##
## Options:
##   -h, --help    Display this message
##   -v, --version <major.minor.patch>  Specify the version of the APK to build

# Set default variables
VERSION="0.0.0"

# Function to display usage and exit
usage() {
  [ "$*" ] && echo "$0: $*"
  sed -n '/^##/,/^$/s/^## \{0,1\}//p' "$0"
  exit 2
}

# Main script logic
main() {
  # Parse command-line arguments
  while [ $# -gt 0 ]; do
    case "$1" in
      (-h|--help) usage 2>&1;;
      (--) shift; break;;
      (-v|--version) VERSION="$2"; shift 2;;
      (-*) usage "$1: unknown option";;
      (*) break;;
    esac
    shift
  done

  # Your script logic goes here
  echo "Script is running..."
  echo "Building APK version: $VERSION"
  eas build --platform android
    echo "DONE"
}

# Execute the main function
main "$@"
