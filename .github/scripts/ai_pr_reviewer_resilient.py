#!/usr/bin/env python3
"""Resilience wrapper for DentalCare AI PR Reviewer.

Adds bounded retries, exponential backoff with jitter, and free-tier model fallbacks
without changing the trusted PR-review implementation.
"""

from __future__ import annotations

import importlib.util
import json
import os
import random
import time
import urllib.error
from pathlib import Path
from typing import Any

RETRYABLE_HTTP_CODES = {408, 429, 500, 502, 503, 504}
DEFAULT_FALLBACK_MODELS = ("gemini-3.7-flash", "gemini-3.5-flash-lite")
USED_MODEL: str | None = None


def load_reviewer():
    script_path = Path(__file__).with_name("ai_pr_reviewer.py")
    spec = importlib.util.spec_from_file_location("dentalcare_ai_pr_reviewer", script_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load base AI PR Reviewer")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reviewer = load_reviewer()
_original_call_gemini = reviewer.call_gemini
_original_render_report = reviewer.render_report


def model_sequence() -> list[str]:
    primary = os.getenv("GEMINI_MODEL", "gemini-3.8-flash").strip()
    configured_fallbacks = os.getenv(
        "GEMINI_FALLBACK_MODELS", ",".join(DEFAULT_FALLBACK_MODELS)
    )
    candidates = [primary, *[item.strip() for item in configured_fallbacks.split(",")]]

    result: list[str] = []
    for model in candidates:
        if model and model not in result:
            result.append(model)
    return result


def retry_delay_seconds(exc: BaseException, attempt_index: int) -> float:
    if isinstance(exc, urllib.error.HTTPError):
        retry_after = exc.headers.get("Retry-After") if exc.headers else None
        if retry_after:
            try:
                return min(float(retry_after), 30.0)
            except ValueError:
                pass

    base = min(2**attempt_index, 8)
    return base + random.uniform(0.15, 0.85)


def is_retryable(exc: BaseException) -> bool:
    if isinstance(exc, urllib.error.HTTPError):
        return exc.code in RETRYABLE_HTTP_CODES
    if isinstance(exc, (urllib.error.URLError, TimeoutError)):
        return True
    if isinstance(exc, (json.JSONDecodeError, RuntimeError)):
        return True
    return False


def resilient_call_gemini(system_instruction: str, user_prompt: str) -> dict[str, Any]:
    global USED_MODEL

    models = model_sequence()
    original_model = os.environ.get("GEMINI_MODEL")
    last_error: BaseException | None = None

    try:
        for model_index, model in enumerate(models):
            max_attempts = 4 if model_index == 0 else 2
            os.environ["GEMINI_MODEL"] = model

            for attempt in range(max_attempts):
                try:
                    print(f"Gemini attempt {attempt + 1}/{max_attempts} using {model}.")
                    analysis = _original_call_gemini(system_instruction, user_prompt)
                    USED_MODEL = model
                    if model_index > 0:
                        print(f"Gemini fallback succeeded using {model}.")
                    return analysis
                except Exception as exc:  # noqa: BLE001 - classified immediately below.
                    last_error = exc
                    if not is_retryable(exc):
                        raise

                    code = exc.code if isinstance(exc, urllib.error.HTTPError) else type(exc).__name__
                    if attempt + 1 < max_attempts:
                        delay = retry_delay_seconds(exc, attempt)
                        print(
                            f"Transient Gemini error ({code}) on {model}; "
                            f"retrying in {delay:.1f}s."
                        )
                        time.sleep(delay)
                    else:
                        print(
                            f"Gemini model {model} remained unavailable after "
                            f"{max_attempts} attempt(s)."
                        )

        if last_error is not None:
            raise last_error
        raise RuntimeError("No Gemini models were configured")
    finally:
        if original_model is None:
            os.environ.pop("GEMINI_MODEL", None)
        else:
            os.environ["GEMINI_MODEL"] = original_model


def resilient_render_report(
    repo: str,
    pr_number: int,
    issue_numbers: list[int],
    model: str,
    analysis: dict[str, Any],
    skipped_sensitive_files: list[str],
) -> str:
    return _original_render_report(
        repo,
        pr_number,
        issue_numbers,
        USED_MODEL or model,
        analysis,
        skipped_sensitive_files,
    )


reviewer.call_gemini = resilient_call_gemini
reviewer.render_report = resilient_render_report


if __name__ == "__main__":
    raise SystemExit(reviewer.main())
