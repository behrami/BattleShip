import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;

public class BattleshipGUI extends javax.swing.JFrame {

// VARIABLES
    // Used to determine click behaviour
    enum SelectionMode {

        PRE_GAME, VERTICAL_HIGHLIGHT, HORIZONTAL_HIGHLIGHT, PLAYER_MOVE, GAME_WIN, GAME_LOSE
    };
    static SelectionMode mode;
    static MousePosition position;
//    
    // Used for ship placement phase
    static final int[] shipSizes = new int[]{1, 1, 2, 2, 4, 6};
    static int placements;
//    
    // Determines game winner; first to reduce other's squares to zero
    static int squaresRemainingPlayer;
    static int squaresRemainingCPU;
//    
    // For A.I
    static Random random = new Random();
    static int[][] possibleMoves;
    static int moveIndex;
//    
    // For saving/resuming games
    static GameState state;
//    
    // For recording statistics
    static ArrayList<PlayerRecord> stats;
    static String currentPlayerName;

// CLASSES    
    public static class MousePosition {

        JTable table;
        int highlightedRow;
        int highlightedColumn;

        public MousePosition(JTable table, int row, int column) {
            this.table = table;
            this.highlightedRow = row;
            this.highlightedColumn = column;
        }
    }

    // conditions of game (for use with save/resume methods)
    public static class GameState implements Serializable {

        // [player/cpu][rows][columns]
        int[][][] boardState;
        String playerName;
        int[][] computerMoves;
        int computerMoveIndex;
        int shipsPlaced;

        public GameState(int[][][] boardState, String name, int shipPlacements,
                int[][] cMoves, int cMoveIndex) {

            this.shipsPlaced = shipPlacements;
            this.boardState = boardState;
            this.playerName = name;
            this.computerMoves = cMoves;
            this.computerMoveIndex = cMoveIndex;
        }
    }
    
    // score is given by difference in squares remaining, e.g.,
    // if you win with 7 squares left, then CPU wins with 5, your average score
    // is (7 -5)/2 = 1
    public static class PlayerRecord implements Serializable {

        String name;
        int wins;
        int losses;
        int bestScore;
        int averageScore;

        public PlayerRecord(String name) {
            this.name = name;
            this.wins = 0;
            this.losses = 0;
            this.bestScore = 0;
            this.averageScore = 0;
        }

        void addMatch(int score) {
            if (score > 0) {
                this.wins++;
                if (score > this.bestScore) {
                    this.bestScore = score;
                }
            } else {
                this.losses++;
            }
            this.averageScore = (averageScore * (wins + losses - 1) + score) / (wins + losses);
        }
    }

    public static class HoverMouseAdapter extends MouseMotionAdapter {

        @Override
        public void mouseMoved(MouseEvent e) {
            JTable table = (JTable) e.getSource();
            position.table = table;
            position.highlightedRow = table.rowAtPoint(e.getPoint());
            position.highlightedColumn = table.columnAtPoint(e.getPoint());
            table.repaint();
        }
    }

    public static class ComputerBoardClickListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            JTable table = (JTable) e.getSource();
            int row = table.rowAtPoint(e.getPoint());
            int column = table.columnAtPoint(e.getPoint());
            if (mode == SelectionMode.PLAYER_MOVE) {
                playerMove(row, column);
            }
        }
    }

    public static class PlayerBoardClickListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            JTable table = (JTable) e.getSource();
            int row = table.rowAtPoint(e.getPoint());
            int column = table.columnAtPoint(e.getPoint());

            if (mode == SelectionMode.HORIZONTAL_HIGHLIGHT) {
                placeShip(shipSizes[placements], row, column, true);
            } else if ((mode == SelectionMode.VERTICAL_HIGHLIGHT)) {
                placeShip(shipSizes[placements], row, column, false);
            }
        }
    }

    // Renderer controls how table models are displayed
    public class BoardRenderer extends JLabel implements
            TableCellRenderer {

        public BoardRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            int shipSize;
            this.setBackground(Color.GRAY);//.gray);
            this.setBorder(new LineBorder(Color.BLACK));

            if (placements <= 5) {
                shipSize = shipSizes[placements];
            } else {
                shipSize = 0;
            }

            if (mode == SelectionMode.HORIZONTAL_HIGHLIGHT && table.equals(playerBoard)) {
                if (table.equals(position.table) && column >= position.highlightedColumn && column < position.highlightedColumn + shipSize
                        && row == position.highlightedRow) {
                    this.setBackground(Color.red);
                }
            } else if (mode == SelectionMode.VERTICAL_HIGHLIGHT && table.equals(playerBoard)) {
                if (table.equals(position.table) && row >= position.highlightedRow && row < position.highlightedRow + shipSize
                        && column == position.highlightedColumn) {
                    this.setBackground(Color.red);
                }
            } else if (mode == SelectionMode.PLAYER_MOVE) {
                if (table.equals(position.table) && row == position.highlightedRow && column == position.highlightedColumn) {
                    this.setBackground(Color.ORANGE);
                }
            }

            // 1 is unhit ship, 2 is hit, 3 is miss
            if (value == 1) {
                if (table.equals(playerBoard)) {
                    this.setBackground(Color.DARK_GRAY);//Color.blue);
                }
            } else if (value == 2) {
                this.setBackground(Color.RED);
            } else if (value == 3) {
                this.setBackground(Color.BLUE);
            }
            table.repaint();
            return this;
        }
    }

// METHODS    
    // Clears board, resets scores for new game
    static void startNewGame() {
        possibleMoves = new int[][]{null, null, null, null};
        moveIndex = -1;
        position = new MousePosition(null, 0, 0);
        placements = 0;
        mode = SelectionMode.PRE_GAME;
        squaresRemainingPlayer = 16;
        squaresRemainingCPU = 16;
    }

    // Changes state of board square that player clicks
    static void playerMove(int row, int column) {

        //makes sure that the current guess is legal, then allows the computer to guess.
        boolean goodGuess = true;

        //0 is an empty square, 1 is occupied by a ship, 2 is a hit, 3 is a miss.
        if (computerBoard.getValueAt(row, column) == 0) {
            computerBoard.setValueAt(3, row, column);
            prompt.setText("Miss.");
        } else if (computerBoard.getValueAt(row, column) == 1) {
            computerBoard.setValueAt(2, row, column);
            prompt.setText("Hit!");
            squaresRemainingCPU--;
        } else if (computerBoard.getValueAt(row, column) == 2) {
            prompt.setText("Take a shot!");
            goodGuess = false;
        } else if (computerBoard.getValueAt(row, column) == 3) {
            prompt.setText("Take a shot!");
            goodGuess = false;
        }

        if (squaresRemainingCPU == 0) {
            mode = SelectionMode.GAME_WIN;
            prompt.setText("You win! Your score: " + squaresRemainingPlayer);
            if (!currentPlayerName.equals("")) {
                recordStats(currentPlayerName, squaresRemainingPlayer);
            }
        } else {
            if (goodGuess) {
                computerMove();
            }
        }
    }

    // Similiar to playerMove but move is decided by an algorithm
    static void computerMove() {

        // indices of shot to be taken
        int row;
        int col;

        //move Index corresponds to search direction in possibleMoves
        // - 1: Try random squre
        // 0: Try Up
        // 1: Try Down
        // 2: Try Left
        // 3: Try Right
        // possibleMoves[moveIndex] null if direction not possible

        if (moveIndex == -1) {

            // picks random, valid square
            row = random.nextInt(10);
            col = random.nextInt(10);
            while ((int) playerBoard.getValueAt(row, col) > 1) {
                row = random.nextInt(10);
                col = random.nextInt(10);
            }

            // if miss, no change
            if ((int) playerBoard.getValueAt(row, col) == 0) {
                playerBoard.setValueAt(3, row, col);

                // random hit  
            } else if ((int) playerBoard.getValueAt(row, col) == 1) {

                squaresRemainingPlayer--;
                playerBoard.setValueAt(2, row, col);
                prompt.setText("Enemy hits your ship!");
                // searches adjacent squares for possible moves
                // search up
                if (row - 1 >= 0) {
                    if ((int) playerBoard.getValueAt(row - 1, col) < 2) {
                        possibleMoves[0] = new int[]{row - 1, col};
                    }
                }
                // search down
                if (row + 1 < 10) {
                    if ((int) playerBoard.getValueAt(row + 1, col) < 2) {
                        possibleMoves[1] = new int[]{row + 1, col};
                    }
                }
                // search left
                if (col - 1 >= 0) {
                    if ((int) playerBoard.getValueAt(row, col - 1) < 2) {
                        possibleMoves[2] = new int[]{row, col - 1};
                    }
                }
                // search right
                if (col + 1 < 10) {
                    if ((int) playerBoard.getValueAt(row, col + 1) < 2) {
                        possibleMoves[3] = new int[]{row, col + 1};
                    }
                }
            }
            // subsequent moves try next possible direction   
        } else {

            row = possibleMoves[moveIndex][0];
            col = possibleMoves[moveIndex][1];

            // if miss, eliminates as possible direction
            if ((int) playerBoard.getValueAt(row, col) == 0) {
                playerBoard.setValueAt(3, row, col);
                possibleMoves[moveIndex] = null;

                // if hit, checks next spot in that direction 
            } else if ((int) playerBoard.getValueAt(row, col) == 1) {

                squaresRemainingPlayer--;
                playerBoard.setValueAt(2, row, col);
                prompt.setText("Enemy hits your ship!");

                switch (moveIndex) {

                    case 0:
                        if (row - 1 < 0) {
                            possibleMoves[0] = null;
                        } else {
                            if ((int) playerBoard.getValueAt(row - 1, col) < 2) {
                                possibleMoves[0] = new int[]{row - 1, col};
                            } else {
                                possibleMoves[0] = null;
                            }
                        }
                        break;

                    case 1:
                        if (row + 1 > 9) {
                            possibleMoves[1] = null;
                        } else {
                            if ((int) playerBoard.getValueAt(row + 1, col) < 2) {
                                possibleMoves[1] = new int[]{row + 1, col};
                            } else {
                                possibleMoves[1] = null;
                            }
                        }
                        break;

                    case 2:
                        if (col - 1 < 0) {
                            possibleMoves[2] = null;
                        } else {
                            if ((int) playerBoard.getValueAt(row, col - 1) < 2) {
                                possibleMoves[2] = new int[]{row, col - 1};
                            } else {
                                possibleMoves[2] = null;
                            }
                        }
                        break;

                    case 3:
                        if (col + 1 > 9) {
                            possibleMoves[3] = null;
                        } else {
                            if ((int) playerBoard.getValueAt(row, col + 1) < 2) {
                                possibleMoves[3] = new int[]{row, col + 1};
                            } else {
                                possibleMoves[3] = null;
                            }
                        }
                        break;
                }
            }
        }
        // sets moveIndex for next move, back to -1 if all directions exhausted
        boolean allNull = true;
        for (int i = 3; i >= 0; i--) {
            if (possibleMoves[i] != null) {
                allNull = false;
                moveIndex = i;
            }
        }
        if (allNull == true) {
            moveIndex = -1;
        }
        //If the player runs out of ships they lose.
        if (squaresRemainingPlayer == 0 && mode != SelectionMode.GAME_LOSE) {
            mode = SelectionMode.GAME_LOSE;
            prompt.setText("You lose! Your Score: " + -squaresRemainingCPU);
            if (!currentPlayerName.equals("")) {
                recordStats(currentPlayerName, squaresRemainingCPU * -1);
            }
        }
    }

    static void placeShip(int length, int row, int column, boolean isHorizontal) {
// this method first checks that the location is not out of bounds and that
// a ship has not already been placed there.
// This and computerPlaceShip are synched;
//computer places one for every player placement
        boolean canPlace = true;

        // Horizontal orientation
        if (isHorizontal) {
            try {
                for (int i = column; i < column + length; i++) {
                    if (playerBoard.getValueAt(row, i) == 1) {
                        canPlace = false;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                canPlace = false;
            }

            if (canPlace) {
                computerPlaceShip(length);
                for (int i = column; i < column + length; i++) {
                    playerBoard.setValueAt(1, row, i);
                }
                prompt.setText("Ship Placed.");

            } else {
                prompt.setText("Can't place here.");
            }

            //Vertical orientation
        } else {
            try {
                for (int i = row; i < row + length; i++) {
                    if (playerBoard.getValueAt(i, column) == 1) {
                        canPlace = false;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                canPlace = false;
            }
            if (canPlace) {
                computerPlaceShip(length);
                for (int i = row; i < row + length; i++) {
                    playerBoard.setValueAt(1, i, column);
                }
                prompt.setText("Ship Placed");
            } else {
                prompt.setText("Can't place here.");
            }
        }
        //When all the ships are placed, start move phase.
        if (placements > 5) {
            hVToggle.setVisible(false);
            prompt.setText("Fire!");
            mode = SelectionMode.PLAYER_MOVE;
        }
    }

    static void computerPlaceShip(int length) {
        // this method first checks that the location is not out of bounds and that
        // a ship has not already been placed there. Same as for player, except with random placement.


        int r = random.nextInt(10);
        int c = random.nextInt(10);
        int isHorizontalInt = random.nextInt(2);
        boolean isHorizontal;

        if (isHorizontalInt == 1) {
            isHorizontal = true;
        } else {
            isHorizontal = false;
        }

        boolean canPlace = true;

        if (isHorizontal) {
            try {
                for (int i = c; i < c + length; i++) {
                    if (computerBoard.getValueAt(r, i) == 1) {
                        canPlace = false;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                canPlace = false;
            }
            if (canPlace) {
                for (int i = c; i < c + length; i++) {
                    computerBoard.setValueAt(1, r, i);
                }
                prompt.setText("Ship Placed");
                placements++;
            } else {
                computerPlaceShip(length);
            }

        } else {
            try {
                for (int i = r; i < r + length; i++) {
                    if (computerBoard.getValueAt(i, c) == 1) {
                        canPlace = false;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                canPlace = false;
            }
            if (canPlace) {
                for (int i = r; i < r + length; i++) {
                    computerBoard.setValueAt(1, i, c);
                }
                placements++;
            } else {
                computerPlaceShip(length);
            }
        }
    }

    static void recordStats(String name, int spread) {

        // doesnt record if anonymous
        boolean exists = false;

        for (PlayerRecord r : stats) {
            if (r.name.equals(name)) {
                exists = true;
                r.addMatch(spread);
            }
        }

        if (!exists) {
            PlayerRecord r = new PlayerRecord(name);
            r.addMatch(spread);
            stats.add(r);
        }
        saveStats();
    }

    static void saveGameState() {
        // values of both boards
        int[][][] currentBoardState =
                new int[2][10][10];

        for (int r = 0; r < 10; r++) {

            for (int c = 0; c < 10; c++) {
                currentBoardState[0][r][c] = (int) playerBoard.getValueAt(r, c);
                currentBoardState[1][r][c] = (int) computerBoard.getValueAt(r, c);
            }
        }

        state = new GameState(currentBoardState, currentPlayerName,
                placements, possibleMoves, moveIndex);

        String fileName = "gamestate.ser";
        //  if (file.exists()){
        try {

            ObjectOutputStream out = new ObjectOutputStream(
                    new FileOutputStream(fileName));
            out.writeObject(state);

        } catch (IOException ex) {
        }
    }

    static void resumeGameState() {

        String fileName = "gamestate.ser";

        File file = new File(fileName);
        if (file.exists()) {
            try {

                ObjectInputStream in = new ObjectInputStream(
                        new FileInputStream(fileName));
                state = (GameState) in.readObject();

                position = new MousePosition(null, 0, 0);
                currentPlayerName = state.playerName;
                possibleMoves = state.computerMoves;
                moveIndex = state.computerMoveIndex;
                mode = SelectionMode.PLAYER_MOVE;
                squaresRemainingPlayer = 0;
                squaresRemainingCPU = 0;
                placements = state.shipsPlaced;
                hVToggle.setVisible(false);
                prompt.setText("Fire!");
                playerNameLabel.setText("Player: "+state.playerName);
                
                int[][] pBoard = state.boardState[0];
                int[][] cBoard = state.boardState[1];

                for (int r = 0; r < 10; r++) {
                    for (int c = 0; c < 10; c++) {
                        playerBoard.setValueAt(pBoard[r][c], r, c);
                        computerBoard.setValueAt(cBoard[r][c], r, c);
                        if (pBoard[r][c] == 1) {
                            squaresRemainingPlayer++;
                        }
                        if (cBoard[r][c] == 1) {
                            squaresRemainingCPU++;
                        }
                    }
                }
            } catch (ClassNotFoundException | IOException ex) {
            }
        } else {
            prompt.setText("No saved game found.");
        }
    }

    static void saveStats() {

        String fileName = "record.ser";
        try {
            ObjectOutputStream out = new ObjectOutputStream(
                    new FileOutputStream(fileName));
            out.writeObject(stats);

        } catch (IOException ex) {
        }
    }

    static void loadStats() {

        String fileName = "record.ser";

        File file = new File(fileName);
        if (file.exists()) {
            try {
                ObjectInputStream in = new ObjectInputStream(
                        new FileInputStream(fileName));
                stats = (ArrayList<PlayerRecord>) in.readObject();

            } catch (ClassNotFoundException | IOException ex) {
            }
        } else {
            stats = new ArrayList();
            saveStats();
        }
    }

    /**
     * Creates new form BattleshipGUI
     */
    public BattleshipGUI() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        newGameDialog = new javax.swing.JDialog();
        nameField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        startGameButton = new javax.swing.JButton();
        gameStatsDialog = new javax.swing.JDialog();
        jScrollPane4 = new javax.swing.JScrollPane();
        statsTable = new javax.swing.JTable();
        clearPlayerStatsButton = new javax.swing.JButton();
        clearAllStatsButton = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        hVToggle = new javax.swing.JButton();
        prompt = new javax.swing.JLabel();
        playerNameLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        playerBoard = new JTable(){

            @Override
            public boolean isCellEditable(int rowIndex, int colIndex){
                return false;
            }
        }
        ;
        computerBoard = new JTable(){
            @Override
            public boolean isCellEditable(int rowIndex, int colIndex){
                return false;
            }
        } ;
        jLabel4 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        newGameMenuItem = new javax.swing.JMenuItem();
        playerStatsMenuItem = new javax.swing.JMenuItem();
        saveGameMenuItem = new javax.swing.JMenuItem();
        loadGameMenuItem = new javax.swing.JMenuItem();

        newGameDialog.setMinimumSize(new java.awt.Dimension(300, 200));
        newGameDialog.setModal(true);
        newGameDialog.setResizable(false);

        jLabel3.setText("Enter your name. Leave blank to play anonymously.");

        startGameButton.setText("Start!");
        startGameButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startGameButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout newGameDialogLayout = new javax.swing.GroupLayout(newGameDialog.getContentPane());
        newGameDialog.getContentPane().setLayout(newGameDialogLayout);
        newGameDialogLayout.setHorizontalGroup(
            newGameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(newGameDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(newGameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(nameField, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(startGameButton))
                .addContainerGap(40, Short.MAX_VALUE))
        );
        newGameDialogLayout.setVerticalGroup(
            newGameDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(newGameDialogLayout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jLabel3)
                .addGap(18, 18, 18)
                .addComponent(nameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(startGameButton)
                .addContainerGap(76, Short.MAX_VALUE))
        );

        gameStatsDialog.setMinimumSize(new java.awt.Dimension(500, 300));
        gameStatsDialog.setModal(true);

        statsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [stats.size()][5]

            ,
            new String [] {
                "Name", "Wins", "Losses", "Average Score","Best Score"
            }
        ));
        jScrollPane4.setViewportView(statsTable);

        clearPlayerStatsButton.setText("Clear Name");
        clearPlayerStatsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearPlayerStatsButtonActionPerformed(evt);
            }
        });

        clearAllStatsButton.setText("Clear All");
        clearAllStatsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearAllStatsButtonActionPerformed(evt);
            }
        });

        jButton1.setText("Clear High Score");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout gameStatsDialogLayout = new javax.swing.GroupLayout(gameStatsDialog.getContentPane());
        gameStatsDialog.getContentPane().setLayout(gameStatsDialogLayout);
        gameStatsDialogLayout.setHorizontalGroup(
            gameStatsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gameStatsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 334, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(gameStatsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(clearPlayerStatsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(clearAllStatsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        gameStatsDialogLayout.setVerticalGroup(
            gameStatsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gameStatsDialogLayout.createSequentialGroup()
                .addGap(83, 83, 83)
                .addComponent(jButton1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(clearPlayerStatsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(clearAllStatsButton)
                .addContainerGap(136, Short.MAX_VALUE))
            .addGroup(gameStatsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addContainerGap())
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        hVToggle.setText("Toggle H/V");
        hVToggle.setVisible(false);
        hVToggle.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hVToggleActionPerformed(evt);
            }
        });

        prompt.setFont(new java.awt.Font("Calibri", 0, 14)); // NOI18N
        prompt.setText("Use the menu to start a new game.");

        playerNameLabel.setFont(new java.awt.Font("Calibri", 0, 18)); // NOI18N
        playerNameLabel.setText("Player");

        jLabel2.setFont(new java.awt.Font("Calibri", 0, 18)); // NOI18N
        jLabel2.setText("Computer");

        playerBoard.setModel(new javax.swing.table.DefaultTableModel(
            new Object[][]{
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
            }, new String[]{
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J"
            }
        ));
        playerBoard.setName("player");
        playerBoard.setFillsViewportHeight(true);
        playerBoard.setMaximumSize(new java.awt.Dimension(300, 300));
        playerBoard.setMinimumSize(new java.awt.Dimension(300, 300));
        playerBoard.setRowHeight(30);
        playerBoard.setOpaque(false);
        playerBoard.addMouseListener(new PlayerBoardClickListener() );
        playerBoard.addMouseMotionListener(new HoverMouseAdapter());
        playerBoard.setDefaultRenderer(Object.class, new BoardRenderer());
        playerBoard.setRowHeight(30);
        playerBoard.setSize(300, 300);

        computerBoard.setModel(new javax.swing.table.DefaultTableModel( new Object [][] {
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0},
            {0, 0, 0, 0,0,0,0,0,0,0}
        },
        new String [] {
            "A","B","C","D","E","F","G","H","I","J"
        }));
        computerBoard.setFillsViewportHeight(true);
        computerBoard.setMaximumSize(new java.awt.Dimension(300, 300));
        computerBoard.setMinimumSize(new java.awt.Dimension(300, 300));
        computerBoard.setPreferredSize(new java.awt.Dimension(300, 300));
        computerBoard.setOpaque(false);
        computerBoard.addMouseListener(new ComputerBoardClickListener());
        computerBoard.addMouseMotionListener(new HoverMouseAdapter());
        computerBoard.setDefaultRenderer(Object.class, new BoardRenderer());
        computerBoard.setRowHeight(30);

        jLabel4.setFont(new java.awt.Font("Calibri", 0, 14)); // NOI18N
        jLabel4.setText("Versus");

        jMenu1.setText("File");

        newGameMenuItem.setText("New Game");
        newGameMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newGameMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(newGameMenuItem);

        playerStatsMenuItem.setText("Player Stats");
        playerStatsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playerStatsMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(playerStatsMenuItem);

        saveGameMenuItem.setText("Save Game");
        saveGameMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveGameMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(saveGameMenuItem);

        loadGameMenuItem.setText("Load Game");
        loadGameMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadGameMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(loadGameMenuItem);

        jMenuBar1.add(jMenu1);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(43, 43, 43)
                        .addComponent(prompt, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hVToggle, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(206, 206, 206))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(playerBoard, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(playerNameLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jLabel4)
                                .addGap(6, 6, 6)))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(computerBoard, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(playerNameLabel)
                    .addComponent(jLabel2)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(playerBoard, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(computerBoard, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 50, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(prompt, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(hVToggle))
                .addGap(31, 31, 31))
        );

        pack();
    }// </editor-fold>                        

    // Listener methods for GUI components
    private void hVToggleActionPerformed(java.awt.event.ActionEvent evt) {                                         
        if (mode == SelectionMode.HORIZONTAL_HIGHLIGHT) {
            mode = SelectionMode.VERTICAL_HIGHLIGHT;
        } else if (mode == SelectionMode.VERTICAL_HIGHLIGHT) {
            mode = SelectionMode.HORIZONTAL_HIGHLIGHT;
        }
    }                                        

  private void startGameButtonActionPerformed(java.awt.event.ActionEvent evt) {                                                
      currentPlayerName = nameField.getText();
      mode = SelectionMode.HORIZONTAL_HIGHLIGHT;
      playerNameLabel.setText("Player: " + currentPlayerName);
      newGameDialog.setVisible(false);
      prompt.setText("Place your ships.");
  }                                               

  private void newGameMenuItemActionPerformed(java.awt.event.ActionEvent evt) {                                                
      for (int r = 0; r < playerBoard.getRowCount(); r++) {
          for (int c = 0; c < playerBoard.getColumnCount(); c++) {
              playerBoard.setValueAt(0, r, c);
              computerBoard.setValueAt(0, r, c);
          }
      }
      hVToggle.setVisible(true);
      startNewGame();
      newGameDialog.setSize(300, 200);
      newGameDialog.setVisible(true);
  }                                               

  private void playerStatsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {                                                    
      statsTable.setModel(new javax.swing.table.DefaultTableModel(
              new Object[stats.size()][5],
              new String[]{
                  "Name", "Wins", "Losses", "Average", "Best"
              }));
      for (int i = 0; i < stats.size(); i++) {

          statsTable.setValueAt(stats.get(i).name, i, 0);
          statsTable.setValueAt(stats.get(i).wins, i, 1);
          statsTable.setValueAt(stats.get(i).losses, i, 2);
          statsTable.setValueAt(stats.get(i).averageScore, i, 3);
          statsTable.setValueAt(stats.get(i).bestScore, i, 4);
      }
      gameStatsDialog.setVisible(true);

  }                                                   

    private void saveGameMenuItemActionPerformed(java.awt.event.ActionEvent evt) {                                                 
        if (mode == SelectionMode.PLAYER_MOVE) {
            saveGameState();
        }
    }                                                

    private void loadGameMenuItemActionPerformed(java.awt.event.ActionEvent evt) {                                                 
        resumeGameState();
    }                                                

    private void clearAllStatsButtonActionPerformed(java.awt.event.ActionEvent evt) {                                                    
        stats = new ArrayList(0);
        statsTable.setModel(new javax.swing.table.DefaultTableModel(
                new Object[stats.size()][5],
                new String[]{
                    "Name", "Wins", "Losses", "Average", "Best"
                }));

        saveStats();
    }                                                   

    private void clearPlayerStatsButtonActionPerformed(java.awt.event.ActionEvent evt) {                                                       

        if (statsTable.getSelectedRow() >= 0) {
            stats.remove(statsTable.getSelectedRow());
        }
        statsTable.setModel(new javax.swing.table.DefaultTableModel(
                new Object[stats.size()][5],
                new String[]{
                    "Name", "Wins", "Losses", "Average", "Best"
                }));
        for (int i = 0; i < stats.size(); i++) {

            statsTable.setValueAt(stats.get(i).name, i, 0);
            statsTable.setValueAt(stats.get(i).wins, i, 1);
            statsTable.setValueAt(stats.get(i).losses, i, 2);
            statsTable.setValueAt(stats.get(i).averageScore, i, 3);
            statsTable.setValueAt(stats.get(i).bestScore, i, 4);
        }
        saveStats();
    }                                                      

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {                                         
         if (statsTable.getSelectedRow() >= 0) {
            stats.get(statsTable.getSelectedRow()).bestScore = 0;
            statsTable.setValueAt(0, statsTable.getSelectedRow(), 4);
            saveStats();
        }
    }                                        

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(BattleshipGUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new BattleshipGUI().setVisible(true);
            }
        });
        //initializes fields and loads stats
        startNewGame();
        loadStats();
    }
    // Variables declaration - do not modify                     
    private javax.swing.JButton clearAllStatsButton;
    private javax.swing.JButton clearPlayerStatsButton;
    private static javax.swing.JTable computerBoard;
    private static javax.swing.JDialog gameStatsDialog;
    private static javax.swing.JButton hVToggle;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JMenuItem loadGameMenuItem;
    private javax.swing.JTextField nameField;
    private static javax.swing.JDialog newGameDialog;
    private javax.swing.JMenuItem newGameMenuItem;
    private static javax.swing.JTable playerBoard;
    private static javax.swing.JLabel playerNameLabel;
    private javax.swing.JMenuItem playerStatsMenuItem;
    private static javax.swing.JLabel prompt;
    private javax.swing.JMenuItem saveGameMenuItem;
    private javax.swing.JButton startGameButton;
    private javax.swing.JTable statsTable;
    // End of variables declaration                   
}
