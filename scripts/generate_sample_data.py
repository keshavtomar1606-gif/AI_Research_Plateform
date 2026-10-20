"""Regenerates backend/src/main/resources/sample-data/student_performance.csv.

The data is SYNTHETIC (made up) so that anyone can reproduce it. Each row is created with:

    final_score = 2 + 3.0*hours_studied + 0.2*attendance_pct + 0.35*previous_score + 1.2*sleep_hours + noise

where noise is random with standard deviation 3.5. A correctly working linear regression should
therefore learn coefficients close to 3.0, 0.2, 0.35 and 1.2, and its MAE should be close to the
noise level (about 2.8 points). Five rows have a missing value to demonstrate data cleaning.

Usage (from the project folder):  python scripts/generate_sample_data.py
Only the Python standard library is needed.
"""
import csv
import os
import random

ROWS = 200
SEED = 2026
OUT = os.path.join(os.path.dirname(__file__), "..", "backend", "src", "main", "resources",
                   "sample-data", "student_performance.csv")


def main():
    rng = random.Random(SEED)
    rows = []
    for i in range(ROWS):
        hours = round(rng.uniform(0, 10), 1)
        attendance = round(rng.uniform(50, 100), 1)
        previous = round(rng.uniform(40, 100), 1)
        sleep = round(rng.uniform(4, 9), 1)
        score = 2 + 3.0 * hours + 0.2 * attendance + 0.35 * previous + 1.2 * sleep + rng.gauss(0, 3.5)
        rows.append([f"S{i + 1:03d}", hours, attendance, previous, sleep, round(score, 1)])

    # Blank out a few values so the training job has to drop incomplete rows.
    for row_index, col_index in [(7, 4), (41, 1), (88, 2), (130, 5), (171, 3)]:
        rows[row_index][col_index] = ""

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["student_id", "hours_studied", "attendance_pct", "previous_score", "sleep_hours", "final_score"])
        writer.writerows(rows)
    print(f"Wrote {ROWS} rows to {os.path.abspath(OUT)}")


if __name__ == "__main__":
    main()
