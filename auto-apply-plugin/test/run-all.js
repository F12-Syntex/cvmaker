'use strict';

// Entry point for `node --test auto-apply-plugin/`: node resolves a directory argument through
// package.json "main", so this file loads every *.test.js file in this folder. Inside the
// folder, `node --test test/*.test.js` (npm test) runs the same files directly.
const fs = require('node:fs');
const path = require('node:path');

for (const file of fs.readdirSync(__dirname).sort()) {
  if (file.endsWith('.test.js')) {
    require(path.join(__dirname, file));
  }
}
