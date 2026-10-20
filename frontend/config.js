// Where the website finds the backend API.
// - On your laptop (localhost) it always uses http://localhost:8080/api.
// - Online, it uses CLOUD_API_BASE. After deploying the backend (e.g. on Render),
//   replace the address below with your own backend address, keeping "/api" at the end.
const CLOUD_API_BASE = "https://YOUR-BACKEND-NAME.onrender.com/api";

const isLocal = ["localhost", "127.0.0.1", ""].includes(location.hostname);
window.RESEARCHAI_API_BASE = window.RESEARCHAI_API_BASE || (isLocal ? "http://localhost:8080/api" : CLOUD_API_BASE);
