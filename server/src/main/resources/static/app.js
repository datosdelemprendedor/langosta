const BASE_URL = 'http://localhost:8000';

async function loadAgents() {
    try {
        const res = await fetch(`${BASE_URL}/agents`);
        const agents = await res.json();
        const container = document.getElementById('agents-list');
        container.innerHTML = agents.length === 0
            ? '<p style="color:#666">No agents connected</p>'
            : agents.map(agent => `
                <div class="card">
                    <h3>${agent.name}</h3>
                    <p>Model: ${agent.model}</p>
                    <p>Task: ${agent.currentTaskId ?? 'Idle'}</p>
                    <span class="badge ${agent.isOnline ? 'COMPLETED' : 'FAILED'}">
                        ${agent.isOnline ? 'Online' : 'Offline'}
                    </span>
                </div>
            `).join('');
    } catch (e) {
        console.error('Error loading agents', e);
    }
}

async function loadTasks() {
    try {
        const res = await fetch(`${BASE_URL}/tasks`);
        const tasks = await res.json();
        const container = document.getElementById('tasks-list');
        container.innerHTML = tasks.length === 0
            ? '<p style="color:#666">No tasks found</p>'
            : tasks.map(task => `
                <div class="card">
                    <h3>${task.title}</h3>
                    <p>${task.description}</p>
                    <p>Agent: ${task.assignedAgentId ?? 'Unassigned'}</p>
                    ${task.result ? `<p>Result: ${task.result}</p>` : ''}
                    <span class="badge ${task.status}">${task.status}</span>
                </div>
            `).join('');
    } catch (e) {
        console.error('Error loading tasks', e);
    }
}

function updateStatus(online) {
    const el = document.getElementById('status');
    el.textContent = online ? 'Connected' : 'Disconnected';
    el.className = `status ${online ? 'online' : 'offline'}`;
}

async function init() {
    try {
        await Promise.all([loadAgents(), loadTasks()]);
        updateStatus(true);
    } catch {
        updateStatus(false);
    }
    setInterval(() => {
        loadAgents();
        loadTasks();
    }, 5000);
}

init();
