// Config
const API = "http://localhost:8080/api/othello";

const boardSize = 8;
const boardDiv = document.getElementById("board");
const indicator = document.getElementById("turn-indicator");
const resetButton = document.getElementById("reset");
const startBtn = document.getElementById("start-btn");

// Coding UI
const codeEditor = document.getElementById("code-editor");
const runCodeBtn = document.getElementById("run-code-btn");
const clearCodeBtn = document.getElementById("clear-code-btn");
const codeOutput = document.getElementById("code-output");
const codingQuestion = document.getElementById("coding-question");

let currentBoard = [];
let currentPlayer = 1;

// ---------- Game logic ----------
async function fetchState() {
  try {
    const res = await fetch(`${API}/state`);
    const state = await res.json();
    currentPlayer = state.currentPlayer;
    renderBoard(state.board, state.lastMove);
    updateTurnIndicator(state.currentPlayer);
    updateCodeSubmissionUI(state.currentPlayer);

    const overlay = document.getElementById("winner-overlay");
    const text = document.getElementById("winner-text");
    overlay.classList.remove("show");
    if (state.winner !== undefined && state.winner !== null) {
      let winnerText = state.winner === 1 ? "🏆 Black Wins!" : state.winner === 2 ? "🏆 White Wins!" : "🤝 It's a Tie!";
      text.textContent = winnerText;
      overlay.classList.add("show");
    }
  } catch (err) {
    console.error(err);
  }
}

async function makeMove(row, col) {
  try {
    await fetch(`${API}/move?row=${row}&col=${col}`, { method: "POST" });
    logMove(row, col);
    await fetchState();
    await new Promise(r => setTimeout(r, 700));
    const aiRes = await fetch(`${API}/move?ai=true`, { method: "POST" });
    const aiState = await aiRes.json();
    if (aiState.lastMove) {
          highlightAIMove(aiState.lastMove[0], aiState.lastMove[1]);
          await new Promise(resolve => setTimeout(resolve, 600));
        }
    renderBoard(aiState.board, aiState.lastMove);
    updateTurnIndicator(aiState.currentPlayer);
    updateCodeSubmissionUI(aiState.currentPlayer);
  } catch (err) {
    console.error(err);
  }
}

async function resetGame() {
  try {
    await fetch(`${API}/reset`, { method: "POST" });
    fetchState();
    clearMoveLog();
  } catch (err) {
    console.error(err);
  }
}


async function startGame() {
  const playerName = document.getElementById("player-name").value.trim();
  if (!playerName) return alert("Enter name");

  try {
    await fetch(`${API}/start?playerName=${encodeURIComponent(playerName)}`, { method: "POST" });
    document.getElementById("start-screen").style.display = "none";
    document.getElementById("main-container").style.display = "flex"; // side-by-side layout
    await fetchState();
  } catch (err) {
    console.error(err);
  }
}



// ---------- Coding sandbox ----------
async function loadCodingQuestion() {
  try {
    const res = await fetch(`${API}/codingQuestion`);
    const data = await res.json();

    const q = data.question || "";
    const tests = data.testCases || [];
    const questionEl = document.getElementById("coding-question");
    const testEl = document.getElementById("testcases");
    const startScreen = document.getElementById("start-screen");
    const mainContainer = document.getElementById("main-container");

    if (q) {
      // ✅ Question available
      questionEl.textContent = q;
      testEl.textContent = tests.length
        ? tests.map((t, i) => `Testcase${i + 1}: ${t}`).join("\n")
        : "No testcases available.";

      // Show start screen only (hide everything else)
      document.body.style.background = "#1c1c1c";
      startScreen.style.display = "block";
      mainContainer.style.display = "none";
    } else {
      // 🚫 No question yet: show blank page
      startScreen.style.display = "none";
      mainContainer.style.display = "none";
    }
  } catch (err) {
    console.error("loadCodingQuestion:", err);
  }
}


async function runCode() {
  if (currentPlayer !== 1) return (codeOutput.textContent = "Only Black Player can submit code!");
  const code = codeEditor.value.trim();
  if (!code) return (codeOutput.textContent = "Please enter code.");

  codeOutput.textContent = "Running...";
  runCodeBtn.disabled = true;

  try {
    // ✅ send code as JSON body, not query param
    const res = await fetch(`${API}/submitCode`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code })
    });

    const result = await res.json();
    if (result.success) {
      let out = "";
      if (result.compile_output) out += "Compile Output:\n" + result.compile_output + "\n\n";
      if (result.stderr) out += "Error:\n" + result.stderr + "\n\n";
      if (result.stdout) out += "Output:\n" + result.stdout;
      else out += "No output produced.";
      codeOutput.textContent = out;
    } else {
      codeOutput.textContent = "Error: " + (result.error || "Failed");
    }
  } catch (err) {
    codeOutput.textContent = "Error: " + err.message;
  } finally {
    runCodeBtn.disabled = false;
  }
}


function clearCode() {
  codeEditor.value = "";
  codeOutput.textContent = "Output will appear here...";
}

// ---------- UI helpers ----------
function renderBoard(board, lastMove = null) {
  boardDiv.innerHTML = "";
  for (let r = 0; r < boardSize; r++) {
    for (let c = 0; c < boardSize; c++) {
      const cell = document.createElement("div");
      cell.classList.add("cell");
      cell.dataset.row = r;
      cell.dataset.col = c;
      const v = board[r][c];
      if (v === 1) cell.classList.add("black");
      if (v === 2) cell.classList.add("white");
      cell.addEventListener("click", () => makeMove(r, c));
      boardDiv.appendChild(cell);
    }
  }
  currentBoard = board.map(r => [...r]);
}

function updateTurnIndicator(cp) {
  indicator.textContent = `Current Turn: ${cp === 1 ? "Black" : "White"}`;
}

function highlightAIMove(r, c) {
  const cell = document.querySelector(`.cell[data-row="${r}"][data-col="${c}"]`);
  if (cell) {
    cell.classList.add("ai-move");
    setTimeout(() => cell.classList.remove("ai-move"), 600);
  }
}

function logMove(row, col) {
  const moveLog = document.getElementById("move-log");
  const li = document.createElement("li");
  li.textContent = `(${row + 1}, ${col + 1})`;
  moveLog.appendChild(li);
}

function clearMoveLog() {
  document.getElementById("move-log").innerHTML = "";
}

function updateCodeSubmissionUI(player) {
  if (player === 1) {
    runCodeBtn.disabled = false;
    codeEditor.disabled = false;
  } else {
    runCodeBtn.disabled = true;
    codeEditor.disabled = false;
  }
}

// ---------- Init ----------
document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("main-container").style.display = "block";
  fetchState();
  loadCodingQuestion();

  if (startBtn) startBtn.addEventListener("click", startGame);
  if (resetButton) resetButton.addEventListener("click", resetGame);
  if (runCodeBtn) runCodeBtn.addEventListener("click", runCode);
  if (clearCodeBtn) clearCodeBtn.addEventListener("click", clearCode);
});











