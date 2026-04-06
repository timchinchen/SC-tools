#!/bin/bash
set -e

echo "=== SC-Tools Environment Setup ==="

# Install JDK 21 if not present
if ! java -version 2>&1 | grep -q "21"; then
  echo "Installing OpenJDK 21 via Homebrew..."
  brew install openjdk@21 2>/dev/null || true
  # Create symlink for system Java wrappers
  if [ -d "/usr/local/opt/openjdk@21" ]; then
    sudo ln -sfn /usr/local/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || true
    export JAVA_HOME="/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

# Verify Java
echo "Java version:"
java -version 2>&1 || { echo "ERROR: Java not available after install"; exit 1; }

# Install Maven if not present
if ! command -v mvn &> /dev/null; then
  echo "Installing Maven via Homebrew..."
  brew install maven 2>/dev/null || true
fi

# Verify Maven
echo "Maven version:"
mvn --version 2>&1 || { echo "ERROR: Maven not available after install"; exit 1; }

# Fix npm cache permissions (needed for agent-browser)
if [ -d "$HOME/.npm" ]; then
  npm cache clean --force 2>/dev/null || true
fi

# Initialize git repo if not already
if [ ! -d ".git" ]; then
  echo "Initializing git repository..."
  git init
  cat > .gitignore << 'EOF'
target/
*.class
*.jar
!lib/*.jar
.idea/
*.iml
.DS_Store
data/
*.mv.db
*.trace.db
EOF
  git add -A
  git commit -m "Initial commit: project setup"
fi

# Install Maven dependencies if pom.xml exists
if [ -f "pom.xml" ]; then
  echo "Installing Maven dependencies..."
  mvn dependency:resolve -q 2>/dev/null || true
fi

echo "=== Setup Complete ==="
