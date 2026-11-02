import unittest
from checkpoint_lifecycle import run_with_checkpoint

class LifecycleTests(unittest.TestCase):
    def test_restore_precedes_session_and_save(self):
        events=[]
        result=run_with_checkpoint(read_checkpoint=lambda:b'old', restore_checkpoint=lambda x:events.append(('restore',x)),run_session=lambda:events.append(('run',)) or 3,capture_checkpoint=lambda:events.append(('capture',)) or b'new',write_checkpoint=lambda x:events.append(('verified_write',x)))
        self.assertEqual(result,3); self.assertEqual(events,[('restore',b'old'),('run',),('capture',),('verified_write',b'new')])
    def test_restore_failure_never_runs_or_writes(self):
        events=[]
        def fail(x): raise RuntimeError('bad checkpoint')
        with self.assertRaises(RuntimeError): run_with_checkpoint(read_checkpoint=lambda:b'old',restore_checkpoint=fail,run_session=lambda:events.append('run'),capture_checkpoint=lambda:b'new',write_checkpoint=lambda x:events.append('write'))
        self.assertEqual(events,[])
    def test_capture_failure_does_not_write(self):
        events=[]
        with self.assertRaises(ValueError): run_with_checkpoint(read_checkpoint=lambda:None,restore_checkpoint=lambda x:None,run_session=lambda:None,capture_checkpoint=lambda:b'',write_checkpoint=lambda x:events.append('write'))
        self.assertEqual(events,[])
    def test_write_failure_is_reported(self):
        def fail(x): raise RuntimeError('storage failure')
        with self.assertRaises(RuntimeError): run_with_checkpoint(read_checkpoint=lambda:None,restore_checkpoint=lambda x:None,run_session=lambda:None,capture_checkpoint=lambda:b'new',write_checkpoint=fail)

if __name__=='__main__': unittest.main()
