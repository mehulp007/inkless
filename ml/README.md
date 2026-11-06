# ml/

[`compare_models.ipynb`](compare_models.ipynb) compares the app's on-device Naive Bayes category
model with a standard TF-IDF + logistic regression (scikit-learn), on the app's demo data, with the
same time-ordered split. It's what led to the model weighting a known merchant more heavily.

- `data/demo_payments.csv`: five months of made-up demo payments (no real data, ever).
- `data/naive_bayes.json`: the app's model on the same split and in a replay, written by the app's
  `MlDataExport` unit test.

To regenerate both and re-run the notebook:

```bash
INKLESS_ML_EXPORT=1 ./gradlew :app:testDebugUnitTest --tests "*MlDataExport*"
cd ml && python -m venv .venv && .venv/bin/pip install -r requirements.txt   # .venv\Scripts\ on Windows
.venv/bin/python build_notebook.py
```
