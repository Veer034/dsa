# LLD: Chess & Tic Tac Toe

> **Experience Level:** 10+ Years Java | Spring Boot · Kafka · Redis · MySQL · Elasticsearch · ScyllaDB · Druid

---

## 🔁 Clarifying Questions (You → Interviewer)

| # | Question | Why It Matters |
|---|----------|----------------|
| 1 | Should I design Chess, Tic Tac Toe, or both? Which is the focus? | Scope alignment |
| 2 | Is this single-player vs AI, or two-player (human vs human)? | Game loop design |
| 3 | Do we need network/multiplayer support or local only? | Session and state management |
| 4 | Should the design support other board games in the future (Checkers, etc.)? | Abstraction/extensibility needs |
| 5 | Do we need move validation (illegal moves), undo/redo? | Feature scope |
| 6 | Is persistence of game state required (pause/resume)? | Serialization needs |
| 7 | Do we need a game timer (chess clock)? | Timer threading concerns |

---

## 🔁 Expected Follow-up Questions (Interviewer → You)

- "How does your Board know if a move is valid without being coupled to piece logic?"
- "How would you implement undo/redo?"
- "How do you detect checkmate vs stalemate in Chess?"
- "Can your design support 3-player Tic Tac Toe?"
- "What does the `move()` method return — what info does the caller need?"
- "How would you serialize the game state to Redis?"

---

## Part 1: Tic Tac Toe

### Enums & Markers

```java
public enum Player {
    X, O;
    public Player opponent() { return this == X ? O : X; }
}

public enum GameStatus {
    IN_PROGRESS, X_WON, O_WON, DRAW
}
```

---

### Board

```java
public class TicTacToeBoard {
    private final int size;
    private final Player[][] grid;

    public TicTacToeBoard(int size) {
        this.size = size;
        this.grid = new Player[size][size];
    }

    public boolean makeMove(int row, int col, Player player) {
        if (row < 0 || row >= size || col < 0 || col >= size) return false;
        if (grid[row][col] != null) return false;
        grid[row][col] = player;
        return true;
    }

    public boolean isFull() {
        for (Player[] row : grid)
            for (Player cell : row)
                if (cell == null) return false;
        return true;
    }

    public Player getCell(int r, int c) { return grid[r][c]; }
    public int getSize() { return size; }
}
```

---

### Win Condition Checker

```java
public class WinChecker {

    public Player checkWinner(TicTacToeBoard board) {
        int n = board.getSize();

        // Check rows and columns
        for (int i = 0; i < n; i++) {
            Player rowWinner = checkLine(board, i, 0, 0, 1, n);
            if (rowWinner != null) return rowWinner;
            Player colWinner = checkLine(board, 0, i, 1, 0, n);
            if (colWinner != null) return colWinner;
        }

        // Diagonals
        Player diag1 = checkLine(board, 0, 0, 1, 1, n);
        if (diag1 != null) return diag1;

        Player diag2 = checkLine(board, 0, n - 1, 1, -1, n);
        return diag2; // null if no winner
    }

    private Player checkLine(TicTacToeBoard board, int startR, int startC,
                              int dr, int dc, int length) {
        Player first = board.getCell(startR, startC);
        if (first == null) return null;
        for (int i = 1; i < length; i++) {
            if (board.getCell(startR + i * dr, startC + i * dc) != first)
                return null;
        }
        return first;
    }
}
```

---

### Game

```java
public class TicTacToeGame {
    private final TicTacToeBoard board;
    private final WinChecker winChecker;
    private Player currentPlayer;
    private GameStatus status;

    public TicTacToeGame(int size) {
        this.board = new TicTacToeBoard(size);
        this.winChecker = new WinChecker();
        this.currentPlayer = Player.X;
        this.status = GameStatus.IN_PROGRESS;
    }

    public GameStatus play(int row, int col) {
        if (status != GameStatus.IN_PROGRESS)
            throw new IllegalStateException("Game is over: " + status);

        if (!board.makeMove(row, col, currentPlayer))
            throw new IllegalArgumentException("Invalid move: (" + row + "," + col + ")");

        Player winner = winChecker.checkWinner(board);
        if (winner != null) {
            status = (winner == Player.X) ? GameStatus.X_WON : GameStatus.O_WON;
        } else if (board.isFull()) {
            status = GameStatus.DRAW;
        } else {
            currentPlayer = currentPlayer.opponent();
        }

        return status;
    }

    public GameStatus getStatus() { return status; }
    public Player getCurrentPlayer() { return currentPlayer; }
}
```

---

## Part 2: Chess

### Pieces & Movement

```java
public enum PieceColor { WHITE, BLACK }

public abstract class ChessPiece {
    protected final PieceColor color;
    protected Position position;

    public ChessPiece(PieceColor color, Position position) {
        this.color = color;
        this.position = position;
    }

    public abstract List<Move> getLegalMoves(ChessBoard board);
    public abstract String getSymbol();

    public PieceColor getColor() { return color; }
    public Position getPosition() { return position; }
    public void setPosition(Position p) { this.position = p; }
}
```

---

```java
public class Position {
    private final int row; // 0-7
    private final int col; // 0-7

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public boolean isValid() {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public Position translate(int dr, int dc) {
        return new Position(row + dr, col + dc);
    }

    // equals, hashCode, getters
}
```

---

```java
public class Move {
    private final Position from;
    private final Position to;
    private final ChessPiece capturedPiece; // nullable

    public Move(Position from, Position to, ChessPiece captured) {
        this.from = from;
        this.to = to;
        this.capturedPiece = captured;
    }
    // Getters
}
```

---

### Piece Implementations

```java
public class Rook extends ChessPiece {
    public Rook(PieceColor color, Position position) { super(color, position); }

    @Override
    public List<Move> getLegalMoves(ChessBoard board) {
        List<Move> moves = new ArrayList<>();
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : directions) {
            Position next = position.translate(d[0], d[1]);
            while (next.isValid()) {
                ChessPiece occupant = board.getPiece(next);
                if (occupant == null) {
                    moves.add(new Move(position, next, null));
                } else {
                    if (occupant.getColor() != this.color)
                        moves.add(new Move(position, next, occupant));
                    break;
                }
                next = next.translate(d[0], d[1]);
            }
        }
        return moves;
    }

    @Override public String getSymbol() { return color == PieceColor.WHITE ? "R" : "r"; }
}

public class Bishop extends ChessPiece {
    public Bishop(PieceColor color, Position position) { super(color, position); }

    @Override
    public List<Move> getLegalMoves(ChessBoard board) {
        List<Move> moves = new ArrayList<>();
        int[][] directions = {{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d : directions) {
            Position next = position.translate(d[0], d[1]);
            while (next.isValid()) {
                ChessPiece occupant = board.getPiece(next);
                if (occupant == null) {
                    moves.add(new Move(position, next, null));
                } else {
                    if (occupant.getColor() != this.color)
                        moves.add(new Move(position, next, occupant));
                    break;
                }
                next = next.translate(d[0], d[1]);
            }
        }
        return moves;
    }

    @Override public String getSymbol() { return color == PieceColor.WHITE ? "B" : "b"; }
}

public class Knight extends ChessPiece {
    private static final int[][] OFFSETS = {{2,1},{2,-1},{-2,1},{-2,-1},{1,2},{1,-2},{-1,2},{-1,-2}};

    public Knight(PieceColor color, Position position) { super(color, position); }

    @Override
    public List<Move> getLegalMoves(ChessBoard board) {
        List<Move> moves = new ArrayList<>();
        for (int[] offset : OFFSETS) {
            Position target = position.translate(offset[0], offset[1]);
            if (!target.isValid()) continue;
            ChessPiece occupant = board.getPiece(target);
            if (occupant == null || occupant.getColor() != this.color)
                moves.add(new Move(position, target, occupant));
        }
        return moves;
    }

    @Override public String getSymbol() { return color == PieceColor.WHITE ? "N" : "n"; }
}

public class Pawn extends ChessPiece {
    public Pawn(PieceColor color, Position position) { super(color, position); }

    @Override
    public List<Move> getLegalMoves(ChessBoard board) {
        List<Move> moves = new ArrayList<>();
        int direction = (color == PieceColor.WHITE) ? -1 : 1;
        int startRow  = (color == PieceColor.WHITE) ? 6 : 1;

        // Single forward
        Position oneForward = position.translate(direction, 0);
        if (oneForward.isValid() && board.getPiece(oneForward) == null) {
            moves.add(new Move(position, oneForward, null));
            // Double from start
            if (position.getRow() == startRow) {
                Position twoForward = position.translate(2 * direction, 0);
                if (board.getPiece(twoForward) == null)
                    moves.add(new Move(position, twoForward, null));
            }
        }

        // Diagonal captures
        for (int dc : new int[]{-1, 1}) {
            Position diag = position.translate(direction, dc);
            if (diag.isValid()) {
                ChessPiece target = board.getPiece(diag);
                if (target != null && target.getColor() != this.color)
                    moves.add(new Move(position, diag, target));
            }
        }
        return moves;
    }

    @Override public String getSymbol() { return color == PieceColor.WHITE ? "P" : "p"; }
}
```

---

### Chess Board

```java
public class ChessBoard {
    private final ChessPiece[][] grid = new ChessPiece[8][8];

    public void placePiece(ChessPiece piece, Position pos) {
        grid[pos.getRow()][pos.getCol()] = piece;
        piece.setPosition(pos);
    }

    public ChessPiece getPiece(Position pos) {
        return grid[pos.getRow()][pos.getCol()];
    }

    public void applyMove(Move move) {
        ChessPiece piece = getPiece(move.getFrom());
        grid[move.getFrom().getRow()][move.getFrom().getCol()] = null;
        grid[move.getTo().getRow()][move.getTo().getCol()] = piece;
        piece.setPosition(move.getTo());
    }

    public void undoMove(Move move, ChessPiece captured) {
        ChessPiece piece = getPiece(move.getTo());
        grid[move.getTo().getRow()][move.getTo().getCol()] = captured;
        grid[move.getFrom().getRow()][move.getFrom().getCol()] = piece;
        piece.setPosition(move.getFrom());
        if (captured != null) captured.setPosition(move.getTo());
    }

    public boolean isInCheck(PieceColor kingColor) {
        Position kingPos = findKing(kingColor);
        return getAllPieces(kingColor.opposite()).stream()
                .flatMap(p -> p.getLegalMoves(this).stream())
                .anyMatch(m -> m.getTo().equals(kingPos));
    }

    private Position findKing(PieceColor color) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                ChessPiece p = grid[r][c];
                if (p instanceof King && p.getColor() == color)
                    return new Position(r, c);
            }
        throw new IllegalStateException("King not found for " + color);
    }

    public List<ChessPiece> getAllPieces(PieceColor color) {
        List<ChessPiece> pieces = new ArrayList<>();
        for (ChessPiece[] row : grid)
            for (ChessPiece p : row)
                if (p != null && p.getColor() == color) pieces.add(p);
        return pieces;
    }
}
```

---

### Chess Game

```java
public class ChessGame {
    private final ChessBoard board;
    private PieceColor currentTurn;
    private final Deque<Move> moveHistory = new ArrayDeque<>();
    private GameStatus status;

    public ChessGame() {
        this.board = new ChessBoard();
        this.currentTurn = PieceColor.WHITE;
        this.status = GameStatus.IN_PROGRESS;
        setupBoard();
    }

    public boolean makeMove(Position from, Position to) {
        ChessPiece piece = board.getPiece(from);
        if (piece == null || piece.getColor() != currentTurn) return false;

        Move move = piece.getLegalMoves(board).stream()
                .filter(m -> m.getTo().equals(to))
                .findFirst()
                .orElse(null);
        if (move == null) return false;

        board.applyMove(move);

        // Reject if own king is in check after move
        if (board.isInCheck(currentTurn)) {
            board.undoMove(move, move.getCapturedPiece());
            return false;
        }

        moveHistory.push(move);
        currentTurn = currentTurn.opposite();
        updateStatus();
        return true;
    }

    private void updateStatus() {
        boolean inCheck = board.isInCheck(currentTurn);
        boolean hasLegalMoves = board.getAllPieces(currentTurn).stream()
                .anyMatch(p -> !p.getLegalMoves(board).isEmpty());

        if (!hasLegalMoves) {
            status = inCheck ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
    }

    public void undo() {
        if (moveHistory.isEmpty()) return;
        Move last = moveHistory.pop();
        board.undoMove(last, last.getCapturedPiece());
        currentTurn = currentTurn.opposite();
        status = GameStatus.IN_PROGRESS;
    }

    private void setupBoard() {
        // Place all pieces at initial positions (abbreviated)
        for (int c = 0; c < 8; c++) {
            board.placePiece(new Pawn(PieceColor.WHITE, new Position(6, c)), new Position(6, c));
            board.placePiece(new Pawn(PieceColor.BLACK, new Position(1, c)), new Position(1, c));
        }
        // Rooks, Knights, Bishops, Queens, Kings ...
    }
}
```

---

## Design Patterns Used

| Pattern | Usage |
|---------|-------|
| Strategy | Move generation per piece type |
| Command | `Move` as a command (enables undo) |
| Template Method | `ChessPiece.getLegalMoves()` skeleton |
| State | `GameStatus` tracks game lifecycle |
| Factory | `PieceFactory.create(type, color, pos)` |

---

## Redis: Game State Serialization

```java
// Serialize game state as JSON into Redis with 24h TTL
redisTemplate.opsForValue().set(
    "game:chess:" + gameId,
    objectMapper.writeValueAsString(gameState),
    Duration.ofHours(24)
);
```

---

## Extension Points

- **AI opponent**: Integrate Minimax with alpha-beta pruning
- **En passant, castling, promotion**: Extend `Pawn` and `King` move generation
- **Spectator mode**: Broadcast moves via Kafka topic `game.moves`
- **Leaderboard**: Store Elo ratings in MySQL, cache in Redis