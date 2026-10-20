# Deploying ResearchAI online for free

This guide puts the whole project online without your laptop:

| Part | Free service | What it does |
|------|--------------|--------------|
| Frontend (HTML/CSS/JS) | **Netlify** | Hosts the website |
| Backend (Spring Boot) | **Render** (free web service, using the top-level `Dockerfile`) | Runs the API and ML training |
| Database | **Aiven** free MySQL (or TiDB Cloud) | Stores projects, datasets, training jobs |
| AI | **Groq** free API | Replaces Ollama when online |

> Free plans change over time. Check each site's current limits before you rely on them.
> On your laptop nothing changes: it still uses local MySQL and Ollama by default.

**Before you start, know these limits:**
- **Render's free server sleeps** after about 15 minutes without visitors. The next visit takes about 1 minute to wake it; the website shows "Waking up server…" while it waits. Open the site a few minutes before your demo.
- **Groq's free plan has per-minute and per-day limits.** The backend also limits AI messages to 10 per minute per visitor and 30 per minute in total.
- **No user accounts.** Anyone can *view* the site. Set `APP_ACCESS_KEY` (step 4) so that only people you give the key to can *change data or use the AI*. It is a shared demo password, not a full login system.

---

## Step 1: Get a free Groq API key

1. Go to **https://console.groq.com** and sign up (no card needed).
2. Open **API Keys** → **Create API Key** → copy it (it starts with `gsk_`).
3. **Keep it secret.** Never put it in any file in the project, never commit it, never paste it in chat. You will only paste it into Render (step 4).
4. Check the model list at https://console.groq.com/docs/models. This project uses `openai/gpt-oss-20b` by default (`openai/gpt-oss-120b` gives better answers). Groq retires models over time; if chat says "model … was not found", pick a current chat model from that page and set `AI_MODEL` on Render.

## Step 2: Create a free MySQL database (Aiven)

1. Go to **https://aiven.io**, sign up, and create a **MySQL** service on the **Free** plan.
2. When it is running, open the service's **Overview** page and note: **Host**, **Port**, **User** (usually `avnadmin`), **Password**, and the database name (`defaultdb`).
3. Your database URL is:
   ```
   jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED
   ```
   For example: `jdbc:mysql://mysql-abc123.aivencloud.com:12345/defaultdb?sslMode=REQUIRED`

You don't need to create tables: the backend creates them automatically on first start.

*Alternative:* TiDB Cloud Serverless (MySQL-compatible). Use the JDBC URL it shows you, which uses port 4000 and requires SSL.

## Step 3: Put the code on GitHub

Your folder is already linked to `https://github.com/keshavtomar1606-gif/AI_Research_Plateform` (a **public** repository).

First, make sure **no password will be uploaded**. Run in PowerShell:

```powershell
cd C:\Users\LOQ\Downloads\AI_Research_Platform_College_Project
git add -A
git status                                   # backend/config/local.properties must NOT be listed
git grep --cached -n "DB_PASSWORD=" -- . ":!*.example" ":!*.md"   # must print nothing
```

Then commit and push:

```powershell
git commit -m "ResearchAI: real ML training, MySQL persistence, cloud deployment support"
git branch -M main
git push -u origin main
```

(If Git asks you to log in, sign in to GitHub in the window that opens.)

## Step 4: Deploy the backend on Render

1. Go to **https://render.com** and sign up with your GitHub account.
2. **New** → **Web Service** → connect the `AI_Research_Plateform` repository.
3. Settings:
   - **Region:** any; choose the one closest to you (e.g. Singapore for India), ideally near your database.
   - **Language / Runtime:** `Docker`
   - **Root Directory:** leave **empty**
   - **Dockerfile Path:** `./Dockerfile` (the default; uses the `Dockerfile` in the top-level folder)
   - **Docker Build Context Directory:** `.` (the default)
   - Don't type `backend` in any of these fields. The top-level Dockerfile already knows where the backend is.
   - **Instance Type:** `Free`
   - **Health Check Path:** `/api/health` (under Advanced, if shown)
4. **Environment Variables:** add these. Values in CAPITALS are yours.
   **Type them without the ` marks.** The marks only show code in this guide. Replace `HOST` and `PORT` with the real values from Aiven.

   Easiest: click **Add from .env** and paste your settings.
   ⚠️ **Don't paste the example below as it is.** `HOST`, `PORT` and the CAPITAL words are placeholders. Pasting them causes `Failed to parse the host:port pair 'HOST:PORT'`.
   Tip: copy it into a private file named `render-env.local.txt` in the project folder (git ignores `*.local.txt`), fill in your real values there, then paste from that file.
   Example (no quotes, no spaces around `=`):
   ```
   DB_URL=jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED
   DB_USERNAME=avnadmin
   DB_PASSWORD=YOUR_AIVEN_PASSWORD
   AI_PROVIDER=groq
   AI_API_KEY=YOUR_GROQ_KEY
   AI_MODEL=openai/gpt-oss-20b
   APP_ACCESS_KEY=MAKE_UP_A_DEMO_PASSWORD
   CORS_ALLOWED_ORIGINS=http://localhost:5500
   ```

   The same settings as a table:

   | Key | Value |
   |-----|-------|
   | `DB_URL` | `jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED` (from step 2) |
   | `DB_USERNAME` | `avnadmin` (from step 2) |
   | `DB_PASSWORD` | your Aiven password |
   | `AI_PROVIDER` | `groq` |
   | `AI_API_KEY` | your Groq key from step 1 |
   | `AI_MODEL` | `openai/gpt-oss-20b` |
   | `APP_ACCESS_KEY` | a demo password you make up, e.g. `research-demo-2026` |
   | `CORS_ALLOWED_ORIGINS` | leave as `http://localhost:5500` for now; you will change it in step 6 |

5. Click **Create Web Service**. The first build takes about 5–10 minutes.
6. When it says **Live**, open `https://YOUR-SERVICE-NAME.onrender.com/api/health`. You should see:
   ```json
   {"status":"UP","database":"UP","ai":"UP","aiProvider":"groq","model":"openai/gpt-oss-20b","accessKeyRequired":true}
   ```
   Copy your backend address (`https://YOUR-SERVICE-NAME.onrender.com`).

## Step 5: Deploy the frontend on Netlify

1. Open `frontend/config.js` and replace the address with your Render address, **keeping `/api` at the end**:
   ```js
   const CLOUD_API_BASE = "https://YOUR-SERVICE-NAME.onrender.com/api";
   ```
2. Commit and push the change:
   ```powershell
   git add frontend/config.js
   git commit -m "Point frontend to Render backend"
   git push
   ```
3. Go to **https://app.netlify.com** → **Add new site** → **Import an existing project** → GitHub → choose the repository.
   - **Base directory:** `frontend`
   - **Build command:** *(leave empty)*
   - **Publish directory:** `frontend`
4. Click **Deploy**. You'll get an address like `https://something.netlify.app`. You can rename it under **Site configuration → Change site name**.

*Quicker alternative:* at **https://app.netlify.com/drop**, drag the `frontend` folder onto the page (after editing `config.js`).

## Step 6: Allow the website to call the backend (CORS)

1. In Render → your service → **Environment**, change:
   ```
   CORS_ALLOWED_ORIGINS = https://something.netlify.app
   ```
   Use your exact Netlify address: `https://`, no `/` at the end. To also keep local development working, use:
   `https://something.netlify.app,http://localhost:5500`
2. Save. Render restarts the backend automatically.

## Step 7: Test it

1. Open your Netlify address. Within about a minute the top bar should say **"Backend · MySQL · AI online · 🔒"**.
2. Create a project. The site asks for the access key once; enter your `APP_ACCESS_KEY`.
3. Model training → **Load sample dataset** → **Train model**. Expect R² ≈ 0.95.
4. AI Assistant → ask a question.
5. Optional automated check from your laptop:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 -BaseUrl https://YOUR-SERVICE-NAME.onrender.com/api -AccessKey YOUR_ACCESS_KEY
   ```

---

## Render environment variables: quick reference

On **Render → your service → Environment**, there must be **exactly one row** for each key below.
Type them **without** backticks or quotes, and replace every CAPITAL placeholder with your real value.

| Key | Value |
|-----|-------|
| `DB_URL` | `jdbc:mysql://AIVEN_HOST:AIVEN_PORT/defaultdb?sslMode=REQUIRED` |
| `DB_USERNAME` | `avnadmin` |
| `DB_PASSWORD` | your Aiven password (Aiven → Overview → Password → click the stars) |
| `AI_PROVIDER` | `groq` |
| `AI_API_KEY` | your Groq key (starts with `gsk_`) |
| `AI_MODEL` | `openai/gpt-oss-20b` |
| `APP_ACCESS_KEY` | a demo password you make up |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5500` for now, later your Netlify address |

Notes:
- `AIVEN_HOST` and `AIVEN_PORT` come from Aiven → your MySQL service → Overview → **Host** and **Port**.
- Use `sslMode=REQUIRED` (Java style), **not** `ssl-mode=REQUIRED` from Aiven's "Service URI".
- Never put real passwords or keys in this file; it is public on GitHub.

### If the Render log says `Failed to parse the host:port pair 'HOST:PORT'`
The backend is still reading the example value. Usually there are two `DB_URL` rows.
1. Render → **Environment** → **Edit**.
2. **Delete** every row that has backticks (`` `DB_URL` ``), contains `HOST`, `PORT`, `YOUR_…` or `MAKE_UP…`, or appears twice.
3. Add the missing keys again from the table above (one row each).
4. Click **Save changes** (or **Save, rebuild, and deploy**) and wait 3–5 minutes.
5. Search the logs for `Caused by`. No new results means it worked; look for **Live**.

### Other Render log messages
| Log says | Fix |
|----------|-----|
| `lstat …/backend/backend: no such file or directory` | Settings → Build & Deploy: Root Directory **empty**, Dockerfile Path `./Dockerfile`, Docker Build Context Directory `.` |
| `Access denied for user 'avnadmin'` | Wrong `DB_PASSWORD`. Copy it from Aiven again, with no spaces. |
| `Communications link failure` | Check the host, the port and `?sslMode=REQUIRED` in `DB_URL` |
| `AI_PROVIDER=groq needs an API key` | Add `AI_API_KEY` |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Site says "Backend offline" | Wait 1 minute (the server is waking up) and click **Retry**. Check that `config.js` has the right address with `/api` at the end. |
| Browser console shows a **CORS** error | `CORS_ALLOWED_ORIGINS` on Render must exactly match your Netlify address (`https://…`, no trailing `/`). |
| Health shows `"database":"DOWN"` or the Render log shows `Communications link failure` / `Access denied` | Check `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. The URL must end with `?sslMode=REQUIRED` for Aiven. |
| Render log: `AI_PROVIDER=groq needs an API key` | Add `AI_API_KEY` in Render → Environment. |
| Chat: "The AI API key is missing or invalid" | Create a new key in the Groq console and update `AI_API_KEY`. |
| Chat: "model … was not found" / "rejected the request" | The model was retired. Choose a current one at console.groq.com/docs/models and update `AI_MODEL`. |
| Chat: "free AI quota is used up" | Groq's free limit was reached. Wait a minute (or until the next day for daily limits). |
| "Wrong access key" | Close the tab, reopen the site, and enter the correct key. |
| Render build fails | Open the build log. Most often the runtime isn't `Docker`, or a Root Directory / Dockerfile setting was changed from the default. |
| Render log: `lstat /opt/render/project/src/backend/backend: no such file or directory` | Settings → Build & Deploy: Root Directory **empty**, Dockerfile Path `./Dockerfile`, Docker Build Context Directory `.`. Click **Save changes** on each, then Manual Deploy → Deploy latest commit. |
| Training jobs show "Interrupted because the backend restarted" | The free server went to sleep during a job. Run it again. |

## Security notes

- Secrets (DB password, Groq key, access key) live **only** in Render's environment settings. They are never in the code, the frontend or GitHub.
- If a key is ever leaked, revoke it in the Groq console and create a new one. If the DB password leaks, change it in Aiven and update Render.
- The access key is a shared password sent over HTTPS. It stops strangers from changing data, but it isn't a substitute for real user accounts.

## Using another AI provider

Any OpenAI-compatible API works. Set `AI_PROVIDER=openai-compatible` together with these variables:

| Provider | `AI_BASE_URL` | `AI_MODEL` (example) |
|----------|---------------|----------------------|
| OpenRouter | `https://openrouter.ai/api/v1` | a model ending in `:free` |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.0-flash` |

and `AI_API_KEY` = that provider's key. Check each provider's documentation for current model names.
