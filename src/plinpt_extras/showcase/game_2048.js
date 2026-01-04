// 2048 Game JS Plugin
// A complete 2048 game implementation in JavaScript
// Demonstrates using plin utilities for navigation

const GRID_SIZE = 4;
const CELL_GAP = 12;

const getTileColor = (value) => {
    const colors = {
        2: { bg: '#eee4da', text: '#776e65' },
        4: { bg: '#ede0c8', text: '#776e65' },
        8: { bg: '#f2b179', text: '#f9f6f2' },
        16: { bg: '#f59563', text: '#f9f6f2' },
        32: { bg: '#f67c5f', text: '#f9f6f2' },
        64: { bg: '#f65e3b', text: '#f9f6f2' },
        128: { bg: '#edcf72', text: '#f9f6f2' },
        256: { bg: '#edcc61', text: '#f9f6f2' },
        512: { bg: '#edc850', text: '#f9f6f2' },
        1024: { bg: '#edc53f', text: '#f9f6f2' },
        2048: { bg: '#edc22e', text: '#f9f6f2' }
    };
    return colors[value] || { bg: '#3c3a32', text: '#f9f6f2' };
};

const createEmptyGrid = () => {
    return Array(GRID_SIZE).fill(null).map(() => Array(GRID_SIZE).fill(0));
};

const addRandomTile = (grid) => {
    const emptyCells = [];
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            if (grid[r][c] === 0) {
                emptyCells.push({ r, c });
            }
        }
    }
    if (emptyCells.length === 0) return grid;
    
    const { r, c } = emptyCells[Math.floor(Math.random() * emptyCells.length)];
    const newGrid = grid.map(row => [...row]);
    newGrid[r][c] = Math.random() < 0.9 ? 2 : 4;
    return newGrid;
};

const slideRow = (row) => {
    let arr = row.filter(x => x !== 0);
    for (let i = 0; i < arr.length - 1; i++) {
        if (arr[i] === arr[i + 1]) {
            arr[i] *= 2;
            arr[i + 1] = 0;
        }
    }
    arr = arr.filter(x => x !== 0);
    while (arr.length < GRID_SIZE) {
        arr.push(0);
    }
    return arr;
};

const rotateGrid = (grid) => {
    const newGrid = createEmptyGrid();
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            newGrid[c][GRID_SIZE - 1 - r] = grid[r][c];
        }
    }
    return newGrid;
};

const moveLeft = (grid) => {
    return grid.map(row => slideRow(row));
};

const moveRight = (grid) => {
    return grid.map(row => slideRow([...row].reverse()).reverse());
};

const moveUp = (grid) => {
    let rotated = rotateGrid(rotateGrid(rotateGrid(grid)));
    rotated = moveLeft(rotated);
    return rotateGrid(rotated);
};

const moveDown = (grid) => {
    let rotated = rotateGrid(grid);
    rotated = moveLeft(rotated);
    return rotateGrid(rotateGrid(rotateGrid(rotated)));
};

const gridsEqual = (a, b) => {
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            if (a[r][c] !== b[r][c]) return false;
        }
    }
    return true;
};

const canMove = (grid) => {
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            if (grid[r][c] === 0) return true;
        }
    }
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            const val = grid[r][c];
            if (r < GRID_SIZE - 1 && grid[r + 1][c] === val) return true;
            if (c < GRID_SIZE - 1 && grid[r][c + 1] === val) return true;
        }
    }
    return false;
};

const getScore = (grid) => {
    let score = 0;
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            score += grid[r][c];
        }
    }
    return score;
};

const hasWon = (grid) => {
    for (let r = 0; r < GRID_SIZE; r++) {
        for (let c = 0; c < GRID_SIZE; c++) {
            if (grid[r][c] === 2048) return true;
        }
    }
    return false;
};

// Component that receives plin utilities
const Game2048Page = ({ plin }) => {
    const [grid, setGrid] = React.useState(() => {
        let g = createEmptyGrid();
        g = addRandomTile(g);
        g = addRandomTile(g);
        return g;
    });
    const [gameOver, setGameOver] = React.useState(false);
    const [won, setWon] = React.useState(false);
    
    // Get current path using plin utilities (demonstration)
    const currentPath = plin ? plin.currentPath() : null;
    
    const resetGame = () => {
        let g = createEmptyGrid();
        g = addRandomTile(g);
        g = addRandomTile(g);
        setGrid(g);
        setGameOver(false);
        setWon(false);
    };
    
    const handleMove = React.useCallback((direction) => {
        if (gameOver) return;
        
        let newGrid;
        switch (direction) {
            case 'left': newGrid = moveLeft(grid); break;
            case 'right': newGrid = moveRight(grid); break;
            case 'up': newGrid = moveUp(grid); break;
            case 'down': newGrid = moveDown(grid); break;
            default: return;
        }
        
        if (!gridsEqual(grid, newGrid)) {
            newGrid = addRandomTile(newGrid);
            setGrid(newGrid);
            
            if (hasWon(newGrid) && !won) {
                setWon(true);
            }
            
            if (!canMove(newGrid)) {
                setGameOver(true);
            }
        }
    }, [grid, gameOver, won]);
    
    React.useEffect(() => {
        const handleKeyDown = (e) => {
            switch (e.key) {
                case 'ArrowLeft': e.preventDefault(); handleMove('left'); break;
                case 'ArrowRight': e.preventDefault(); handleMove('right'); break;
                case 'ArrowUp': e.preventDefault(); handleMove('up'); break;
                case 'ArrowDown': e.preventDefault(); handleMove('down'); break;
            }
        };
        
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleMove]);
    
    const score = getScore(grid);
    
    return React.createElement("div", 
        { className: "p-6 flex flex-col items-center" },
        
        React.createElement("div", 
            { className: "mb-6 text-center" },
            React.createElement("h1", 
                { className: "text-4xl font-bold text-gray-800 mb-2" },
                "2048"
            ),
            React.createElement("p", 
                { className: "text-gray-600" },
                "Use arrow keys to play. Combine tiles to reach 2048!"
            ),
            React.createElement("p", 
                { className: "text-sm text-blue-600 mt-1" },
                "Written in JavaScript, running on PLIN Platform"
            ),
            currentPath && React.createElement("p",
                { className: "text-xs text-gray-400 mt-1" },
                `Current route: ${currentPath}`
            )
        ),
        
        React.createElement("div", 
            { className: "flex items-center gap-4 mb-4" },
            React.createElement("div", 
                { className: "bg-gray-700 text-white px-4 py-2 rounded-lg" },
                React.createElement("div", { className: "text-xs uppercase" }, "Score"),
                React.createElement("div", { className: "text-2xl font-bold" }, score)
            ),
            React.createElement("button", 
                { 
                    className: "bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-semibold transition-colors",
                    onClick: resetGame
                },
                "New Game"
            )
        ),
        
        (gameOver || won) && React.createElement("div", 
            { className: `mb-4 px-4 py-2 rounded-lg font-semibold ${won ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}` },
            won ? "🎉 You Win!" : "Game Over!"
        ),
        
        React.createElement("div", 
            { 
                className: "bg-gray-300 p-3 rounded-lg",
                style: { 
                    display: 'grid',
                    gridTemplateColumns: `repeat(${GRID_SIZE}, 80px)`,
                    gap: `${CELL_GAP}px`
                }
            },
            grid.flat().map((value, index) => {
                const colors = getTileColor(value);
                return React.createElement("div", 
                    { 
                        key: index,
                        className: "w-20 h-20 rounded-lg flex items-center justify-center font-bold transition-all",
                        style: {
                            backgroundColor: value === 0 ? '#cdc1b4' : colors.bg,
                            color: colors.text,
                            fontSize: value >= 1000 ? '24px' : value >= 100 ? '28px' : '32px'
                        }
                    },
                    value !== 0 ? value : ''
                );
            })
        ),
        
        React.createElement("div", 
            { className: "mt-6 grid grid-cols-3 gap-2" },
            React.createElement("div"),
            React.createElement("button", 
                { 
                    className: "bg-gray-200 hover:bg-gray-300 p-3 rounded-lg",
                    onClick: () => handleMove('up')
                },
                "↑"
            ),
            React.createElement("div"),
            React.createElement("button", 
                { 
                    className: "bg-gray-200 hover:bg-gray-300 p-3 rounded-lg",
                    onClick: () => handleMove('left')
                },
                "←"
            ),
            React.createElement("button", 
                { 
                    className: "bg-gray-200 hover:bg-gray-300 p-3 rounded-lg",
                    onClick: () => handleMove('down')
                },
                "↓"
            ),
            React.createElement("button", 
                { 
                    className: "bg-gray-200 hover:bg-gray-300 p-3 rounded-lg",
                    onClick: () => handleMove('right')
                },
                "→"
            )
        ),
        
        React.createElement("div", 
            { className: "mt-6 text-center text-sm text-gray-500" },
            React.createElement("p", null, "Desktop: Use arrow keys"),
            React.createElement("p", null, "Mobile: Use the buttons above")
        )
    );
};

// Factory function that receives plin utilities
const createPageComponent = (plin) => {
    return function PageWrapper() {
        return React.createElement(Game2048Page, { plin: plin });
    };
};

const GameIcon = ["svg", {
    className: "w-5 h-5",
    fill: "none",
    stroke: "currentColor",
    viewBox: "0 0 24 24",
    xmlns: "http://www.w3.org/2000/svg"
}, ["path", {
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth: 2,
    d: "M14 10l-2 1m0 0l-2-1m2 1v2.5M20 7l-2 1m2-1l-2-1m2 1v2.5M14 4l-2-1-2 1M4 7l2-1M4 7l2 1M4 7v2.5M12 21l-2-1m2 1l2-1m-2 1v-2.5M6 18l-2-1v-2.5M18 18l2-1v-2.5"
}]];

return {
    doc: "2048 Game - A complete game implementation in JavaScript using plin utilities",
    
    deps: ["plinpt.i-application", "plinpt.i-js-utils"],
    
    contributions: {
        "plinpt.i-application/nav-items": [{
            id: "game-2048-js",
            parentId: "development",
            label: "2048 (JS)",
            description: "Play the classic 2048 puzzle game",
            route: "game-2048",
            icon: "icon",
            component: "page",
            order: 52
        }]
    },
    
    beans: {
        "page": {
            doc: "2048 game page component with plin utilities",
            type: "react-component",
            value: createPageComponent,
            inject: ["plinpt.i-js-utils/api"]
        },
        "icon": {
            doc: "Navigation icon as Hiccup data",
            type: "hiccup",
            value: GameIcon
        }
    }
};
