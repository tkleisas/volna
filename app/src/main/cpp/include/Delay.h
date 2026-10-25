//
// Created by user on 22/8/2020.
//

#ifndef WAVESYNTH_DELAY_H
#define WAVESYNTH_DELAY_H


class Delay {
public:
    Delay(int length)
    {
        this->len = length > 0 ? length : 1;
        buffer = new float[this->len]();
        this->dtime = this->len;
        this->feedback = 0.0f;
        this->idx = 0;
    }

    ~Delay()
    {
        delete [] buffer;
    }
    void SetDelay(int delaytime)
    {
        if(delaytime < 1) {
            this->dtime = 1;
        } else if(delaytime <= len) {
            this->dtime = delaytime;
        }
        else{
            this->dtime = len;
        }

    }
    void SetFeedback(float feedback)
    {
        if(feedback>0.99)
        {
            this->feedback = 0.99;
        } else if(feedback<0)
        {
            this->feedback = 0.0f;
        }
        else
        {
            this->feedback = feedback;
        }
    }
    void Reset()
    {
        for(int i=0;i<len;i++)
        {
            buffer[i] = 0.0f;
        }
        idx = 0;
    }
    float Process(float in)
    {
        if (dtime <= 0 || idx >= dtime) {
            idx = 0;
        }
        float out = buffer[idx];
        buffer[idx] = in + out*feedback;
        idx++;
        if(idx>=dtime)
            idx = 0;
        return out;

    }
private:
    float* buffer;
    int len;
    float feedback;
    int dtime;
    int idx;

};


#endif //WAVESYNTH_DELAY_H
