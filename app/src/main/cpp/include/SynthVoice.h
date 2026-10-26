#ifndef SynthVoice_h
#define SynthVoice_h

#include "FloatWaveTableOsc.h"
#include "ADSR.h"
#include "LowPass.h"

#include <algorithm>
#include <cmath>


class SynthVoice  {
public:
    SynthVoice()
    {
        init(8000.0f);
    }
    SynthVoice(float newSampleRate) {
        init(newSampleRate);
    }
    ~SynthVoice(void) {

    }
    void SetSampleRate(float newSampleRate)
    {
      this->sampleRate = newSampleRate;
      adsr[0].SetSampleRate((uint32_t)newSampleRate);
      adsr[1].SetSampleRate((uint32_t)newSampleRate);
    }

    void MidiNoteOn(int note, int vel)
    {
        
        float f = pow(2.0, (note - 69.0) / 12.0) * tune;

        velocity = vel/127.0;
        baseFreq1 = f;
        baseFreq2 = f;
        globalBendMul = 1.0f;
        noteBendMul = 1.0f;
        spreadMul1 = 1.0f;
        spreadMul2 = 1.0f;
        updateOscillatorFrequencies();
        adsr[0].Gate(1);
        adsr[1].Gate(1);
        osc[0].ResetPhase();
        osc[1].ResetPhase();
    }
    void MidiChangeNote(int note, int vel)
    {
        float f = pow(2.0, (note - 69.0) / 12.0) * tune;
        velocity = vel/127.0;
        baseFreq1 = f;
        baseFreq2 = f;
        updateOscillatorFrequencies();
    }
    void Lfo1SetFrequency(float frequency)
    {
        lfo[0].SetFrequency(frequency,sampleRate/32);
    }
    void Lfo2SetFrequency(float frequency)
    {
        lfo[1].SetFrequency(frequency,sampleRate/32);
    }
    void MidiNoteOff()
    {
        adsr[0].Gate(0);
        adsr[1].Gate(0);
    }
    void Reset()
    {
        adsr[0].Reset();
        adsr[1].Reset();
    }
    void AddOsc1WaveTable(int len, float *waveTableIn)
    {
        osc[0].AddWaveTable(len,waveTableIn);
    }
    void AddOsc1SharedWaveTable(int len, float *waveTableIn)
    {
        osc[0].AddSharedWaveTable(len, waveTableIn);
    }
    void AddOsc2WaveTable(int len, float *waveTableIn)
    {
        osc[1].AddWaveTable(len,waveTableIn);
    }
    void AddOsc2SharedWaveTable(int len, float *waveTableIn)
    {
        osc[1].AddSharedWaveTable(len, waveTableIn);
    }
    void AddLfo1SharedWaveTable(int len, float *waveTableIn)
    {
        lfo[0].AddSharedWaveTable(len,waveTableIn);
    }
    void AddLfo2SharedWaveTable(int len, float *waveTableIn)
    {
        lfo[1].AddSharedWaveTable(len,waveTableIn);
    }
    void SetOsc1ADSR(uint8_t a, float d,float s,float r)
    {
        adsr[0].SetADSR(a,d,s,r);
    }
    void SetOsc2ADSR(uint8_t a, float d, float s, float r)
    {
        adsr[1].SetADSR(a,d,s,r);
    }
    void SetFmod1(uint8_t fmod)
    {
      this->fmod1 = fmod/64;
    }
    void SetFmod2(uint8_t fmod)
    {
      this->fmod2 = fmod/64;
    }
    void SetFmod3(uint8_t fmod)
    {
      this->fmod3 = fmod/64;
    }
    void noteSpread( int note, int spread)
    {
        spread = spread-63;
        float factor = pow(2.0,1.0/12.0)/100.0;
        float amount = (float)spread/63.0;
        spreadMul1 = 1.0f + factor*amount;
        spreadMul2 = 1.0f - factor*amount;
        updateOscillatorFrequencies();
    }
    void SetOctaveFactor(float factor)
    {
        this->octave_factor = factor;
    }
    void MidiBend(int bend)
    {
      float factor = ((bend - 8192.0)/8192.0);
      globalBendMul = pow(octave_factor, factor);
      updateOscillatorFrequencies();
    }

    void SetNoteBend(float bendSemitones)
    {
      bendSemitones = std::max(-48.0f, std::min(48.0f, bendSemitones));
      noteBendMul = pow(2.0f, bendSemitones/12.0f);
      updateOscillatorFrequencies();
    }

    void ControlChange(uint8_t channel, uint8_t control, uint8_t value)
    {
      //AudioNoInterrupts();
      switch(control)
      {
          case 1: //Modulation
            MidiMod(value);
          break;
          case 16: //Osc1Wave
            MidiOsc1Wave(value);
          break;
          case 17: //Osc2Wave
            MidiOsc2Wave(value);
          break;
          case 18: //osc1 attack
            adsr[0].SetAttackMidi(value);
          break;
          case 19: //osc1 decay
            adsr[0].SetDecayMidi(value);
          break;
          case 20: //osc1 sustain
            adsr[0].SetSustainMidi(value);
          break;
          case 21: //osc1 release
            adsr[0].SetReleaseMidi(value);
          break;

          case 22: //osc2 attack
            adsr[1].SetAttackMidi(value);
          break;
          case 23: //osc2 decay
            adsr[1].SetDecayMidi(value);
          break;
          case 24: //osc2 sustain
            adsr[1].SetSustainMidi(value);
          break;
          case 25: //osc2 release
            adsr[1].SetReleaseMidi(value);
          break;
          case 26: //filter freq
            lowpass.SetParameters(value*(1.0/127.0),lowpass.GetRes());
          break;
          case 27: //filter res
            lowpass.SetParameters(lowpass.GetFreq(),value*(1.0/127.0));
          break;
          case 28: //PWM
            MidiPwm(value);
          break;
		  case 71: //OSC 1 waveform
			  if (value>=0 && value <= 64)
			  {
				  int wave = getMidiOsc1Wave();
				  int v = (int)value;
				  int newval = wave + v;
				  if (newval > 255)
				  {
					  newval = newval-255;
				  }
				  MidiOsc1Wave(newval);
				  
			  } else if (value > 64 && value <=127)
			  {
				  int wave = getMidiOsc1Wave();
				  int v = (int)value;
				  int newval = wave - (128-v);
				  if (newval < 0)
				  {
					  newval = 256+ newval;
				  }
				  MidiOsc1Wave(newval);
			  }
			  
			  break;
		  case 72: //OSC 1 volume
			  if (value < 64)
			  {
				  int newval = GetOsc1Volume() + value;
				  if (newval > 127)
				  {
					  newval = 127;
				  }
				  SetOsc1Volume(newval);

			  }
			  else if (value > 64)
			  {
				  int newval = GetOsc1Volume() - (128 - (int)value);
				  if (newval < 0)
				  {
					  newval = 0;
				  }
				  SetOsc1Volume(newval);
			  }

			  break;
		  case 73: //OSC 1 phase offset
			  if (value < 64)
			  {
				  int newval = GetOsc1PhaseOffset() + value;
				  if (newval > 127)
				  {
					  newval = 127;
				  }
				  SetOsc1PhaseOffset(newval);

			  }
			  else if (value > 64)
			  {
				  int newval = GetOsc1PhaseOffset() - (128 - (int)value);
				  if (newval < 0)
				  {
					  newval = 0;
				  }
				  SetOsc1PhaseOffset(newval);
			  }

			  break;
		  case 75: //OSC 2 waveform
			  if (value < 64)
			  {
				  int newval = getMidiOsc2Wave() + value;
				  if (newval > 255)
				  {
					  newval = newval - 255;
				  }
				  MidiOsc2Wave(newval);

			  }
			  else if (value > 64)
			  {
				  int newval = getMidiOsc2Wave() - (128-value);
				  if (newval < 0)
				  {
					  newval = 256 + newval;
				  }
				  MidiOsc2Wave(newval);
			  }

			  break;
		  case 76: //OSC 2 volume
			  if (value < 64)
			  {
				  int newval = GetOsc2Volume() + value;
				  if (newval > 127)
				  {
					  newval = 127;
				  }
				  SetOsc2Volume(newval);

			  }
			  else if (value > 64)
			  {
				  int newval = GetOsc2Volume() - (128 - value);
				  if (newval < 0)
				  {
					  newval = 0;
				  }
				  SetOsc2Volume(newval);
			  }

			  break;
		  case 77: //OSC 2 phase
			  if (value < 64)
			  {
				  int newval = GetOsc2PhaseOffset() + value;
				  if (newval > 127)
				  {
					  newval = 127;
				  }
				  SetOsc2PhaseOffset(newval);

			  }
			  else if (value > 64)
			  {
				  int newval = GetOsc2PhaseOffset() - (128 - value);
				  if (newval < 0)
				  {
					  newval = 0;
				  }
				  SetOsc2PhaseOffset(newval);
			  }

			  break;

      }

      //AudioInterrupts();
    }
    void MidiMod(uint8_t newmod)
    {
      modulation = newmod/127.0;
      fmod1 = 1.0f - 0.1f*modulation;
      fmod2 = 1.0f - 0.1f*modulation;
      fmod3 = 0.5f*modulation;
    }
    void MidiPwm(uint8_t newmod)
    {
      pwm = newmod/127.0;
      if(newmod == 0)
      {
        osc[0].SetPhaseOffset(0);
        osc[1].SetPhaseOffset(0);
      }
      else
      {
        osc[0].SetPhaseOffset(pwm);
        osc[1].SetPhaseOffset(pwm);
      }
    }
    int GetOsc1WaveTableCount()
    {
      return osc[0].GetWaveTableCount();
    }
    int GetOsc2WaveTableCount()
    {
      return osc[1].GetWaveTableCount();
    }
    void SetOsc1PhaseOffset(uint8_t newphase)
    {
      osc[0].SetPhaseOffset(newphase/127.0);
    }
	uint8_t GetOsc1PhaseOffset()
	{
		return (uint8_t)(osc[0].GetPhaseOffset() * 127.0);
	}
    void SetOsc2PhaseOffset(uint8_t newphase)
    {
      osc[1].SetPhaseOffset(newphase/127.0);
    }
	uint8_t GetOsc2PhaseOffset()
	{
		return (uint8_t)(osc[1].GetPhaseOffset() * 127.0);
	}
	void SetOsc1Volume(uint8_t volume)
	{
		vol1 = ((float)volume)/127.0;
	}
	uint8_t GetOsc1Volume()
	{
		return (uint8_t)(vol1*127.0);
	}

	void SetOsc2Volume(uint8_t volume)
	{
		vol2 = ((float)volume)/127.0;
	}
	uint8_t GetOsc2Volume()
	{
		return (uint8_t)(vol2*127.0);
	}
    void MidiOsc1Wave(uint8_t newwave)
    {
      osc[0].SetWaveTable(newwave);
      wt1_idx = newwave;
    }
	int getMidiOsc1Wave()
	{
		return wt1_idx;
	}
    void MidiOsc2Wave(uint8_t newwave)
    {
      osc[1].SetWaveTable(newwave);
      wt2_idx = newwave;
    }
	int getMidiOsc2Wave()
	{
		return wt2_idx;
	}
    void SetFilterParameters(uint8_t filter_freq, uint8_t filter_q)
    {
      lowpass.SetParameters(filter_freq/127.0,filter_q/127.0);
    }
    float Process()
    {
      if(modulation<=0.01)
      {
        return 0.5*(lowpass.Process(velocity*adsr[0].Process()*vol1*osc[0].Process()*fmod1+velocity*adsr[1].Process()*vol2*osc[1].Process()*fmod2));
      }
      else
      {
        return  0.5*lowpass.Process((velocity*adsr[0].Process()*vol1*osc[0].Process()*fmod1) + (velocity*adsr[1].Process()*vol2*osc[1].Process()*fmod2) + (velocity*(adsr[0].Process()*vol1*osc[0].Process()*vol2*osc[1].Process()*fmod3)));
      }
    }
    bool IsPlaying()
    {
        return adsr[0].GetState() != ADSR::envState::env_idle ||
               adsr[1].GetState() != ADSR::envState::env_idle;
    }
	void SetTet(float tet)
	{
		this->tet = tet;
	}
	void SetTune(float tune)
	{
		this->tune = tune;
	}
protected:
    void updateOscillatorFrequencies()
    {
        osc[0].SetFrequency(baseFreq1*globalBendMul*noteBendMul*spreadMul1,
                            sampleRate);
        osc[1].SetFrequency(baseFreq2*globalBendMul*noteBendMul*spreadMul2,
                            sampleRate);
    }

    void init(float newSampleRate)
    {
        this->sampleRate = newSampleRate;
        this->modulation = 0.0f;
        this->pwm = 0.5f;
        this->vol1 = 1.0f;
        this->vol2 = 1.0f;
        this->fmod1 = 1.0f;
        this->fmod2 = 1.0f;
        this->fmod3 = 0.0f;
        this->ffreq = 1.0f;
        this->fq = 0.0f;
        this->baseFreq1 = 0.0f;
        this->baseFreq2 = 0.0f;
        this->globalBendMul = 1.0f;
        this->noteBendMul = 1.0f;
        this->spreadMul1 = 1.0f;
        this->spreadMul2 = 1.0f;
        this->velocity = 0.0f;
        this->wt1_idx = 0;
        this->wt2_idx = 0;
        lowpass.SetParameters(ffreq, fq);
        adsr[0].SetSampleRate((uint32_t)newSampleRate);
        adsr[1].SetSampleRate((uint32_t)newSampleRate);
    }

    FloatWaveTableOsc osc[2];
    ADSR adsr[2];
    FloatWaveTableOsc lfo[2];
	float vol1;
	float vol2;
    float sampleRate = 44100.0f;
    float baseFreq1 = 0.0f;
    float baseFreq2 = 0.0f;
    float globalBendMul = 1.0f;
    float noteBendMul = 1.0f;
    float spreadMul1 = 1.0f;
    float spreadMul2 = 1.0f;
    float velocity = 0.0f;
    float modulation;
    float pwm;
    float fmod1;
    float fmod2;
    float fmod3;
    float ffreq;
    float fq;
    float octave_factor=2.0;
    LowPass lowpass;
    uint8_t wt1_idx;
    uint8_t wt2_idx;
	float tet = 12.0;
	float tune = 440.0;
};

#endif
