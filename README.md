# BitTutor - Version Control Engine

A zero-dependency VCS written from scratch in Java - implementing Git's internal plumbing using DAGs, Merkle Trees, and Myers diffing.



## Table of Contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Core Data Models](#core-data-models)
- [Command Reference](#command-reference)
  - [bit init](#bit-init)
  - [bit edit](#bit-edit)
  - [bit commit](#bit-commit)
  - [bit branch](#bit-branch)
  - [bit checkout](#bit-checkout)
  - [bit diff](#bit-diff)
  - [bit merge](#bit-merge)
  - [bit rebase](#bit-rebase)
  - [bit log](#bit-log)
- [Quickstart](#quickstart)
- [Design Philosophy](#design-philosophy)

---

## Overview

**Bit** is a ground-up reimplementation of a version control system in Java. Rather than using flat-file change logs, Bit models repository history using the same structural primitives that power Git:

- **Directed Acyclic Graphs (DAGs)** for commit history
- **Hierarchical Merkle Trees** for workspace snapshotting
- **Myers Diff Algorithm** ($O(ND)$) for shortest-path edit scripts
- **SHA-256 content addressing** for deduplication and integrity
- **Radix Trie** for short-hash autocomplete

The engine is entirely in-memory (no external DB) and operates on a sandboxed `bit-playground/` directory generated at runtime.

---

## Project Structure

```
bit-vcs/
├── src/
│   ├── cli/
│   │   ├── BitCommand.java           # Unified functional command interface
│   │   ├── CommandParser.java        # Lexical tokenizer for console input
│   │   ├── CommandRouter.java        # O(1) Functional registry mapping commands
│   │   └── commands/
│   │       ├── BranchCommand.java    # Handles branch tracking logic references
│   │       ├── CheckoutCommand.java  # Workspace rehydration and timeline switching
│   │       ├── CommitCommand.java    # Concrete class for Merkle tree serialization
│   │       ├── DiffCommand.java      # Concrete class for Myers line evaluations
│   │       ├── EditCommand.java      # Subprocess editor bridge for file mutations
│   │       ├── InitCommand.java      # Concrete class for environment configuration
│   │       ├── MergeCommand.java     # 3-Way Merkle reconciliation loop
│   │       └── RebaseCommand.java    # Chronological patch graph replay subsystem
│   ├── engine/
│   │   ├── DiffEngine.java           # Myers O(ND) shortest edit script engine
│   │   ├── FileSystemIO.java         # Native OS subprocess text editor integration
│   │   ├── HashingUtility.java       # SHA-256 cryptographic content hash generator
│   │   ├── LRUCache.java             # 5-slot composite key LRU file-diff cache
│   │   ├── MerkleTreeHelper.java     # Decoupled recursive tree traversal utility
│   │   ├── StorageEngine.java        # In-memory object & commit database
│   │   └── TrieEngine.java           # Radix prefix trie for short SHA resolution
│   └── model/
│       ├── MerkleNode.java           # Base marker interface for content hashing
│       ├── BlobNode.java             # Raw text file content container
│       ├── DirectoryTree.java        # Hierarchical tree entry map (Blobs & Trees)
│       └── CommitNode.java           # Directed snapshot node with parent link tracks
│
└── bit-playground/                   # [Runtime isolated sandbox working directory]
```

The engine source (`src/`) and the runtime sandbox (`bit-playground/`) are intentionally separated. The Java process manages one; the user operates in the other.

---

## Core Data Models

### `BlobNode`

Stores raw file contents. Two identical files in different directories share the same `BlobNode` instance in the object database (content-addressed deduplication).

### `DirectoryTree`

Represents a single directory as a Merkle node. Holds a map of `ChildName → SHA-256 hash`, where each child is either a `BlobNode` (file) or a nested `DirectoryTree` (subdirectory). A folder's own SHA-256 is derived by sorting, joining, and hashing its children's signatures — meaning any change in any descendant propagates upward.

### `CommitNode`

An immutable snapshot pointer containing:

| Field | Type | Notes |
|---|---|---|
| `rootTreeHash` | `String` | SHA-256 of the entire workspace Merkle root at time of commit |
| `parentHashes` | `List<String>` | 0 = root commit, 1 = linear, 2 = merge |
| `hash` | `String` | Commit hash |
| `timestamp` | `long` | Epoch milliseconds |
| `message` | `String` | Commit message |

---

## Command Reference

### `bit init`

Initializes a fresh tracking workspace.

**Steps:**
1. Creates `./bit-playground/` if it does not exist.
2. Wipes the in-memory `objectDatabase` and `commitDatabase`.
3. Initializes `branchPointers` with `"main" → null`.
4. Inserts `"main"` into the autocomplete Trie.

---

### `bit edit [-n] <path>`

Opens a tracked file in the native OS text editor, or allocates a new file/folder node.

**Steps:**
1. Normalizes OS-specific separators (`\` → `/`) for uniform Merkle path keys.
2. Queries the Trie for `<path>`.
   - **Hit:** Opens the existing file in the native editor.
   - **Miss + `-n` flag:** Infers type by presence of a `.` extension. Creates missing parent directories recursively, writes an empty file stub, registers the path in the Trie, and launches the editor. Paths without an extension are treated as directories and created via `mkdir()`.
   - **Miss, no `-n`:** Prompts the user with a `(y/n)` confirmation loop before proceeding.

---

### `bit commit -m "<message>"`

Locks the current workspace state into an immutable Merkle DAG node.

**Steps:**
1. **Conflict pre-flight:** Scans all files for unresolved conflict markers (`<<<<<<< HEAD`). Aborts if any are found.
2. **Recursive Merkle rollup**:
   - Files: Hash content → store `BlobNode` → return `blob:filename:sha`.
   - Folders: Recurse into children → sort signatures alphabetically → join and hash → store `DirectoryTree` → return `tree:foldername:sha`.
3. Reads the current `HEAD` commit hash as `Parent 1`.
4. Derives `CommitID = SHA-256(message + rootTreeHash + timestamp + parentsList)`.
5. Saves the `CommitNode`, advances the current branch pointer, and inserts the ID into the Trie.

---

### `bit branch <name>`

Creates a new branch pointer at the current `HEAD`.

**Steps:**
1. Validates that `<name>` is alphanumeric and does not begin with a hyphen.
2. Resolves the SHA currently referenced by `HEAD`.
3. Inserts `<name> → activeCommitSHA` into `branchPointers`.
4. Registers `<name>` in the Radix Trie for autocomplete. — $O(1)$ amortized.

---

### `bit checkout <target>`

Moves `HEAD` to a different branch or historical commit and rehydrates the working directory.

**Steps:**
1. **Dirty-workspace guard:** Computes the live Merkle root hash of the physical disk and compares it against the stored `rootTreeHash` of the active `HEAD` commit. Aborts with an *"Uncommitted changes"* error if they differ.
2. Resolves `<target>` via the Trie (short SHAs like `c7c7288` are valid).
3. If `<target>` is a key in `branchPointers`, attaches `HEAD` to the branch name. Otherwise, detaches `HEAD` directly onto the raw commit SHA.
4. **Workspace rehydration:** Recursively executes a top-down destructive purge of the local working directory to clear untracked ghost artifacts, then unpacks the target commit's nested DirectoryTree nodes to recreate the exact directory hierarchy and rehydrate text file blocks.

---

### `bit diff [commitA] [commitB]`

Computes the shortest edit script between two states using the Myers Diff algorithm.

**Two modes:**

- **Live vs. HEAD:** Flattens the HEAD commit tree into a `Map<RelativePath, BlobSHA>`. Takes the union of those paths and the live files on disk. Passes historical and live text lines to the Diff Engine.
- **Commit A vs. Commit B:** Flattens both historical commit trees into `Path → SHA` maps. Takes their union, retrieves Blob texts from the object database, and passes them to the Diff Engine.

**Myers Diff Engine:**
- Greedy dynamic-programming graph search over an edit grid.
- Time complexity: $O(ND)$, where $N$ = total lines and $D$ = edit distance.
- Outputs colorized `+` (addition) and `-` (deletion) strings.
- Results are cached in a 5-slot LRU cache keyed by `(FileName + BlobHash + LiveHashCode)`.

---

### `bit merge <branch>`

Performs a 3-way reconciliation of two divergent history lines.

**Steps:**
1. **LCA detection:** Runs a simultaneous double-BFS upward through parent pointers from both `HEAD` and `<branch>` to find their Lowest Common Ancestor commit.
2. **Fast-path checks:**
   - LCA == target branch tip → Already up to date. No-op.
   - LCA == HEAD → Fast-forward: advance the branch pointer without creating a merge commit.
3. **3-way file reconciliation:**
   - Flattens LCA, current, and target trees into three `Path → SHA` maps.
   - Iterates over the union of all paths:
     - **Agreed** (Current SHA == Target SHA): Keep as-is.
     - **Clean update** (Current SHA == LCA SHA): Overwrite local file silently with the target Blob.
     - **True conflict** (both Current and Target diverge from LCA): Injects standard conflict markers (`<<<<<<< / ======= / >>>>>>>`) into the file, opens the native editor, and blocks the thread in a `while()` loop until the markers are manually resolved and saved.
4. Automatically creates a dual-parent `CommitNode` to record the merge.

---

### `bit rebase <branch>`

Linearizes divergent history by replaying local commits on top of the target branch tip.

**Steps:**
1. Locates the LCA of `HEAD` and `<branch>`.
2. Collects all commits on the current branch post-LCA. Reverses the list to chronological order (oldest first).
3. Sets `movingBaselineSHA = targetBranchTipSHA`.
4. For each patch commit:
   - Rehydrates the disk to `movingBaselineSHA`.
   - Performs a 3-way Merkle file patch against `(movingBaseline, PatchCommit, PatchCommitParent)`. Resolves text conflicts via the native editor loop.
   - Snaps a new Merkle root from the resolved disk state.
   - Creates a new `CommitNode` with the original commit message but with `movingBaselineSHA` as its sole parent (history is linearized — no merge node).
   - Advances `movingBaselineSHA` to the newly created commit SHA.
5. Updates the current branch pointer to the final rebased commit ID.

---

### `bit log`

Prints the repository's commit history in topological order.

**Steps:**
1. Initializes a `Set<String> visited` and `List<CommitNode> order`.
2. Executes a post-order DFS from `HEAD` through parent pointers — producing a valid topological sort where children are always processed before parents.
3. Reverses the list to output newest-to-oldest.
4. Scans `branchPointers` to attach floating branch labels (e.g., `HEAD -> feature, main`) to matching commit hashes.
5. Formats the epoch timestamp as `EEE MMM dd HH:mm:ss yyyy Z` and prints each commit block.

---

## Quickstart

Run the following sequence to exercise all core subsystems:

```bash
bit init
bit edit -n welcome.txt
# Enter: "Version 1" → Save & Close
bit commit -m "Root commit"

bit branch experimental
bit edit welcome.txt
# Enter: "Version 1 - Main line" → Save & Close
bit commit -m "Main update"

bit checkout experimental
bit edit welcome.txt
# Enter: "Version 1 - Experimental line" → Save & Close
bit commit -m "Experimental update"

bit checkout main
bit merge experimental
# Conflict editor opens — remove markers, save, and close

bit log
```

---

## Design Philosophy

Bit makes deliberate architectural tradeoffs that favor transparency over production robustness:

- **In-memory only.** The object and commit databases live entirely within JVM memory maps. This bypasses disk serialization bottlenecks, resulting in O(1) tracking lookups. While state resets on process exit, it makes the engine internals fully inspectable during live debug sessions.
- **Content-addressed storage.** Every object (Blob, Tree, Commit) is stored by its SHA-256 hash. This is identical to Git's object model — identical content has exactly one representation regardless of path or history.
- **No index/staging area.** Bit commits directly from the working tree. This is a simplification of Git's three-tree model (working tree → index → HEAD), reducing one layer of complexity.
- **Single-process, blocking I/O.** Conflict resolution loops block the main thread. This avoids concurrency complexity while still correctly modelling the sequential nature of manual conflict resolution.