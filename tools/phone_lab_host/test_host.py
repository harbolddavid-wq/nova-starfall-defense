from host_start import validate_environment
import pytest

def test_strong_distinct_credentials_and_port():
    assert validate_environment({'PHONE_LAB_AGENT_TOKEN':'A'*40,'PHONE_LAB_CONTROL_TOKEN':'B'*40,'PORT':'10000'}) == 10000

def test_reject_short_credentials():
    with pytest.raises(ValueError):
        validate_environment({'PHONE_LAB_AGENT_TOKEN':'short','PHONE_LAB_CONTROL_TOKEN':'B'*40})

def test_reject_matching_credentials():
    with pytest.raises(ValueError):
        validate_environment({'PHONE_LAB_AGENT_TOKEN':'A'*40,'PHONE_LAB_CONTROL_TOKEN':'A'*40})

def test_reject_invalid_port():
    with pytest.raises(ValueError):
        validate_environment({'PHONE_LAB_AGENT_TOKEN':'A'*40,'PHONE_LAB_CONTROL_TOKEN':'B'*40,'PORT':'70000'})