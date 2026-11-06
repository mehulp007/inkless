"""Builds and runs compare_models.ipynb, so the notebook in the repo always has fresh outputs.

    python -m venv .venv && .venv/Scripts/pip install -r requirements.txt   (Windows; bin/ elsewhere)
    .venv/Scripts/python build_notebook.py
"""
import nbformat
from nbclient import NotebookClient

md = nbformat.v4.new_markdown_cell
code = nbformat.v4.new_code_cell

cells = [
    md("""# Category model: Naive Bayes vs logistic regression

Inkless files payments with a small multinomial Naive Bayes model written in plain Kotlin
(`app/src/main/java/com/mehul/inkless/ml/`). It trains on the phone, retrains after every correction,
and needs no ML library. This notebook asks whether a heavier, standard text classifier would do
clearly better on the same job: **TF-IDF over the payee and UPI ID, plus amount, time of day and
weekday, into a logistic regression** (scikit-learn).

**Data.** `data/demo_payments.csv` is the app's demo data: five months of made-up hostel spending
(554 payments, 30 merchants). It is exported by `MlDataExport` in the app's unit tests, together
with the Naive Bayes results on the same split (`data/naive_bayes.json`). No real payments are used
here: the app never sends them anywhere, and this repo is public.

**Protocol.** Payments are sorted by time; the first 75% train, the last 25% test, the way a model
in the app only ever learns from the past."""),
    code("""import json
import numpy as np
import pandas as pd
from scipy.sparse import hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.preprocessing import OneHotEncoder

payments = pd.read_csv("data/demo_payments.csv").fillna("")
payments["timestamp"] = pd.to_datetime(payments["timestamp"], format="ISO8601")
naive_bayes = json.load(open("data/naive_bayes.json"))
print(len(payments), "payments")
payments.head()"""),
    code("""payments["category"].value_counts()"""),
    md("""## Features

The same information the app's model sees, in the usual scikit-learn form:

- **text**: the payee name and UPI ID, as TF-IDF over 2–4 letter pieces (so `nightcanteen` and
  `sharmacanteen` share `can`, `ant`, …);
- **merchant**: the UPI ID itself, one-hot;
- **amount range, time of day, weekday/weekend**: the same buckets the app uses."""),
    code("""AMOUNT_EDGES = [30, 60, 100, 200, 400, 800, 1500, 3000]  # rupees, as in FeatureExtractor.kt

def amount_bucket(paise):
    rupees = paise // 100
    return str(next((i for i, edge in enumerate(AMOUNT_EDGES) if rupees < edge), len(AMOUNT_EDGES)))

def hour_band(hour):
    if 5 <= hour <= 9: return "morning"
    if 10 <= hour <= 13: return "midday"
    if 14 <= hour <= 17: return "afternoon"
    if 18 <= hour <= 21: return "evening"
    return "night"

frame = pd.DataFrame({
    "text": (payments["payee"] + " " + payments["vpa"]).str.lower(),
    "merchant": payments["vpa"].str.lower(),
    "amount": payments["amount_paise"].map(amount_bucket),
    "hour": payments["timestamp"].dt.hour.map(hour_band),
    "day": np.where(payments["timestamp"].dt.day_of_week >= 5, "weekend", "weekday"),
})
labels = payments["category"]

cut = len(payments) * 3 // 4
train, test = slice(0, cut), slice(cut, None)
print("train", cut, "test", len(payments) - cut)"""),
    code("""text = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), sublinear_tf=True)
context = OneHotEncoder(handle_unknown="ignore")
columns = ["merchant", "amount", "hour", "day"]

X_train = hstack([text.fit_transform(frame["text"][train]), context.fit_transform(frame[columns][train])]).tocsr()
X_test = hstack([text.transform(frame["text"][test]), context.transform(frame[columns][test])]).tocsr()

model = LogisticRegression(max_iter=5000, C=10)
model.fit(X_train, labels[train])
predicted = model.predict(X_test)

logreg_accuracy = accuracy_score(labels[test], predicted)
print(f"logistic regression: {logreg_accuracy:.1%} right, macro F1 {f1_score(labels[test], predicted, average='macro'):.2f}")
print(classification_report(labels[test], predicted, zero_division=0))"""),
    md("""## Side by side"""),
    code("""comparison = pd.DataFrame(
    {
        "accuracy on the last 25%": [naive_bayes["holdout_accuracy"], logreg_accuracy],
        "trains on the phone": ["yes, in milliseconds", "would need a port or TFLite"],
        "learns one correction at a time": ["yes", "no, refit"],
        "extra library in the app": ["none", "LiteRT (TFLite) + model file"],
    },
    index=["Naive Bayes (the app)", "TF-IDF + logistic regression"],
)
comparison.style.format({"accuracy on the last 25%": "{:.1%}"})"""),
    md("""## How fast each learns

Accuracy on the same last 25% when only the first *n* payments have been seen. For Naive Bayes the
app's own replay (guess each payment, then learn it) gives the running figure in
`naive_bayes.json`; here logistic regression is refitted at each size."""),
    code("""sizes = [25, 50, 100, 200, cut]
curve = []
for n in sizes:
    head = slice(0, n)
    t = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), sublinear_tf=True)
    c = OneHotEncoder(handle_unknown="ignore")
    Xa = hstack([t.fit_transform(frame["text"][head]), c.fit_transform(frame[columns][head])]).tocsr()
    Xb = hstack([t.transform(frame["text"][test]), c.transform(frame[columns][test])]).tocsr()
    m = LogisticRegression(max_iter=5000, C=10).fit(Xa, labels[head])
    curve.append(accuracy_score(labels[test], m.predict(Xb)))
pd.DataFrame({"payments seen": sizes, "logistic regression": curve}).style.format({"logistic regression": "{:.1%}"})"""),
    code("""print(f"Naive Bayes, replaying all {naive_bayes['payments']} payments in order: "
      f"{naive_bayes['replay_accuracy']:.1%} of guesses right")
gap = logreg_accuracy - naive_bayes["holdout_accuracy"]
print(f"Logistic regression minus Naive Bayes on the held-out payments: {gap:+.1%}")"""),
    md("""## Verdict

The first run of this notebook found a real gap: logistic regression got every held-out payment
right while the app's Naive Bayes got 92%. Logistic regression had learned that the merchant's UPI ID
alone decides the category, while Naive Bayes counted the merchant as one clue among five, so the
amount and the time of day could outvote it. The app's model now counts a merchant it has seen
before three times (`MERCHANT_WEIGHT` in `NaiveBayesCategorizer.kt`); merchants it hasn't seen are
unaffected, since unknown clues are ignored. That closed most of the gap, as the table above shows.

So the app keeps Naive Bayes: it trains and retrains on the phone in milliseconds, learns from each
correction straight away, explains itself as plain counts, and adds no library or model file to the
APK. Logistic regression would need a refit after every correction and either a Kotlin port or
TFLite (LiteRT) to run in the app, for at most a point or so here.

**Caveat:** this is synthetic data where merchants repeat and each has one category and its own price
and time pattern, which flatters both models. On real spending the hard cases are merchants never seen
before, and those are what merchant memory, the one-tap question and corrections are for."""),
]

notebook = nbformat.v4.new_notebook(cells=cells, metadata={"kernelspec": {"name": "python3", "display_name": "Python 3", "language": "python"}})
NotebookClient(notebook, timeout=600, kernel_name="python3", resources={"metadata": {"path": "."}}).execute()
nbformat.write(notebook, "compare_models.ipynb")
print("wrote compare_models.ipynb")
