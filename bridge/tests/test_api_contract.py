import inspect
import unittest

import app as bridge_app


class ApiContractTests(unittest.TestCase):
    def test_stream_endpoint_accepts_provider(self):
        signature = inspect.signature(
            bridge_app.stream_track
        )
        self.assertIn(
            "provider",
            signature.parameters,
        )

    def test_stream_endpoint_accepts_quality(self):
        signature = inspect.signature(
            bridge_app.stream_track
        )
        self.assertIn(
            "quality",
            signature.parameters,
        )


if __name__ == "__main__":
    unittest.main()
