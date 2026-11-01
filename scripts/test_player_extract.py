import unittest

import player_extract as px

# Excerpts copied from the REAL players (probe log 2026-10-30).
C74 = ('gzX=function(J,W,H,D){var Y=[];H=g.t(H);for(var p=H.next();!p.done;p=H.next()){var E=p.value;if(E.url){p=new g.kw(E.url,!0);'
       'if(E.s){var w=p,Z=E.sp,M=BW(27,6907,TZ(9,4260,E.s));w[Q[26]](Z,qo(8,6459,M))}}}} '
       'rUM=function(J){try{var W=(new g.kw(J,!0)).get("n");if(W){return J}}catch(D){g.MR(D)}return J}; '
       'tR=function(J,W,H){H&&(H=BW(26,6906,TZ(25,4276,H)),J[Q[26]](W,qo(9,6458,H)));return J}; signatureTimestamp:20677,')
B0D = ('Lb1=function(y,X,n,B){if(G.url){f=new g.eg(G.url,!0);if(G.s){var N=f,D=G.sp,T=EP(4,4216,sy(61,4843,G.s));N[v[2]](D,Az(7,1879,T))}}} '
       'KjG=function(y){try{var X=(new g.eg(y,!0)).get("n");if(X){return y}}catch(B){g.gk(B)}return y}; "signatureTimestamp":20676')


class PlayerExtractTest(unittest.TestCase):
    def test_reproduces_the_hand_solved_entries(self):
        c = px.build_candidate("c74cbcd6", C74)[0]
        self.assertEqual(c["signatureTimestamp"], 20677)
        self.assertEqual(c["sigJsExpression"], "BW(27,6907,TZ(9,4260,INPUT))")
        self.assertIn("new g.kw('https://x.googlevideo.com/videoplayback?n='+n,true)", c["nJsExpression"])
        d = px.build_candidate("b0d2d49a", B0D)[0]
        self.assertEqual(d["sigJsExpression"], "EP(4,4216,sy(61,4843,INPUT))")
        self.assertIn("new g.eg(", d["nJsExpression"])

    def test_only_the_s_call_site_counts_not_the_other_nested_calls(self):
        # tR's BW(26,6906,TZ(25,4276,H)) has the same shape but reads H, not `.s`: it must not be taken.
        self.assertEqual(px.extract_sig_expression(C74), "BW(27,6907,TZ(9,4260,INPUT))")

    def test_never_guesses(self):
        self.assertEqual(px.build_candidate("x", "nothing here"), (None, "signatureTimestamp not found"))
        self.assertEqual(px.build_candidate("x", 'signatureTimestamp:1'), (None, "no single signature call site"))
        two = "A(1,2,B(3,4,x.s)) C(1,2,D(3,4,y.s)) signatureTimestamp:1"
        self.assertIsNone(px.extract_sig_expression(two), "two different .s sites = ambiguous = no candidate")
        self.assertEqual(px.build_candidate("x", 'A(1,2,B(3,4,x.s)) signatureTimestamp:1'), (None, "no single n URL class"))


if __name__ == "__main__":
    unittest.main()
