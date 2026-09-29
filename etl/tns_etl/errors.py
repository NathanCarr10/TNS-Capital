"""Exceptions raised by the ETL pipeline."""


class StageError(Exception):
    """A pipeline stage (extract, transform or load) failed after all retries.

    Keeps the stage name and the original exception so the run log and the
    caller can report exactly where the pipeline stopped.
    """

    def __init__(self, stage: str, cause: BaseException):
        super().__init__(f"{stage} stage failed: {type(cause).__name__}: {cause}")
        self.stage = stage
        self.cause = cause


class SchemaError(ValueError):
    """Source data is missing columns the transform needs. Not retryable."""
