import importlib.util
import sys
import types
import unittest
from pathlib import Path

smoke=types.ModuleType('mcp_hosted_smoke')
smoke.oidc=lambda audience: None
smoke.BASE='https://example.invalid'
smoke.FIXTURE='fixture'
sys.modules['mcp_hosted_smoke']=smoke
ctl=types.ModuleType('controller')
ctl.PrivatePhoneLab=object
sys.modules['controller']=ctl
spec=importlib.util.spec_from_file_location('supervisor',Path(__file__).with_name('live_supervisor.py'))
s=importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)

class Agent:
    def __init__(self, fail=False): self.stopped=False; self.fail=fail
    def poll(self): return 1 if self.stopped or self.fail else None
    def terminate(self): self.stopped=True
    def wait(self, timeout): return 0

class TestSupervisor(unittest.TestCase):
    def test_rotations_preserve_vm_and_refresh_both_credentials(self):
        clock=[0]; started=[]; audiences=[]; stopped=[]
        def token(a): audiences.append(a); return a
        def spawn(t):
            a=Agent(); started.append(a); return a
        def sessions(t):
            return ([{'session_id':str(len(started)), 'device':{'api_level':'36'}}]
                    if started and not started[-1].stopped else [])
        s.run_supervisor(get_token=token,start_agent=spawn,get_sessions=sessions,
                         now=lambda:clock[0],sleep=lambda n:clock.__setitem__(0,clock[0]+n),
                         hold_seconds=10,rotate_seconds=4,register_seconds=2)
        self.assertEqual(len(started),3)
        self.assertTrue(all(a.stopped for a in started))
        self.assertEqual(audiences.count('nova-phone-lab-agent'),3)
        self.assertEqual(audiences.count('nova-phone-lab-controller'),3)
    def test_unrelated_existing_session_is_not_registration(self):
        clock=[0]; started=[]
        def spawn(_): a=Agent(); started.append(a); return a
        def sessions(_): return [{'session_id':'old', 'device':{'api_level':'36'}}]
        with self.assertRaisesRegex(RuntimeError,'did not register'):
            s.run_supervisor(get_token=lambda a:a,start_agent=spawn,get_sessions=sessions,
                             now=lambda:clock[0],sleep=lambda n:clock.__setitem__(0,clock[0]+n),
                             hold_seconds=10,rotate_seconds=4,register_seconds=2)
        self.assertTrue(started[0].stopped)
    def test_agent_failure_is_not_success(self):
        with self.assertRaisesRegex(RuntimeError,'exited'):
            s.run_supervisor(get_token=lambda a:a,start_agent=lambda _:Agent(True),get_sessions=lambda _:[],
                             now=lambda:0,sleep=lambda n:None,hold_seconds=10,rotate_seconds=4,register_seconds=2)
    def test_invalid_window_rejected(self):
        with self.assertRaises(ValueError):
            s.run_supervisor(get_token=lambda a:a,start_agent=lambda _:Agent(),get_sessions=lambda _:[],
                             now=lambda:0,sleep=lambda n:None,hold_seconds=10000,rotate_seconds=1)
if __name__=='__main__': unittest.main()
