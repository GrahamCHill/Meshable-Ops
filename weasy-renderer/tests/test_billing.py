import unittest
from plugins.billing.renderer import billing_html, BillingPdf


class BillingTests(unittest.TestCase):
    def value(self):
        return dict(number="INV-TEST",kind="INVOICE",issuer="Example <company>",issuerAddress="Address",vatNumber="VAT123",client="Client",billingAddress="Client address",billingEmail="billing@example.test",project="Example",period="2026-01-01 – 2026-01-31",issued="2026-02-01",due="2026-03-01",currency="USD",taxPercent="15",net="100.00",tax="15.00",total="115.00",lines=[dict(description="Domain <script>alert(1)</script>",quantity="1",unit="100.00",net="100.00",tax="15.00",total="115.00")])

    def test_net_tax_and_untrusted_text(self):
        html = billing_html(self.value())
        self.assertIn("Unit excl. tax", html)
        self.assertIn("Subtotal excluding tax", html)
        self.assertIn("&lt;script&gt;", html)
        self.assertNotIn("<script>", html)

    def test_provisional_is_not_a_payment_request(self):
        value = self.value()
        value["kind"] = "PROVISIONAL"
        self.assertIn("not payable", billing_html(value))

    def test_nonfinite_amounts_rejected(self):
        value = self.value()
        value["total"] = "NaN"
        with self.assertRaises(ValueError):
            BillingPdf.model_validate(value)

    def test_payment_details_are_escaped_and_only_shown_for_invoices(self):
        value = self.value()
        value["paymentInstructions"] = 'Bank <example>\nhttps://pay.example/?ref="invoice"'
        html = billing_html(value)
        self.assertIn("Payment details", html)
        self.assertIn("Bank &lt;example&gt;", html)
        value["kind"] = "QUOTE"
        self.assertNotIn("Payment details", billing_html(value))


if __name__ == "__main__":
    unittest.main()
