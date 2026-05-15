#!/usr/bin/env bash
set -euo pipefail

mvn test
npm test
npm run typecheck
